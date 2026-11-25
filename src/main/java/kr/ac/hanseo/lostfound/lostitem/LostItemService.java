package kr.ac.hanseo.lostfound.lostitem;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.hanseo.lostfound.catalog.Building;
import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.Category;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.common.time.TimeProvider;
import kr.ac.hanseo.lostfound.lostitem.LostItemResponses.Created;
import kr.ac.hanseo.lostfound.lostitem.LostItemResponses.Detail;
import kr.ac.hanseo.lostfound.user.StudentRepository;
import lombok.RequiredArgsConstructor;

/** 분실 신고. 신고자 본인만 보고 고칠 수 있다 (공개 목록 없음) */
@Service
@RequiredArgsConstructor
public class LostItemService {

    /** 이 상태의 신청이 연결돼 있으면 분실 신고를 지울 수 없다 */
    private static final List<ClaimStatus> ONGOING = List.of(ClaimStatus.PENDING, ClaimStatus.APPROVED);

    private final LostItemRepository lostItemRepository;
    private final CategoryRepository categoryRepository;
    private final BuildingRepository buildingRepository;
    private final StudentRepository studentRepository;
    private final ClaimRequestRepository claimRequestRepository;
    private final MatchingService matchingService;
    private final TimeProvider timeProvider;

    /** 등록하고 같은 트랜잭션에서 매칭 Top 3를 계산해 함께 돌려준다 */
    @Transactional
    public Created create(LostItemRequest request, SessionUser user) {
        validateNotFuture(request);
        LostItem item = new LostItem(studentRepository.getReferenceById(user.loginId()),
                findCategory(request.categoryId()), request.itemName().trim(), request.lostAt(),
                findBuilding(request.buildingId()), blankToNull(request.locationDetail()),
                blankToNull(request.description()));
        lostItemRepository.saveAndFlush(item);
        return new Created(item.getId(), matchingService.topMatches(item));
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id, SessionUser user) {
        return Detail.from(findOwned(id, user));
    }

    @Transactional(readOnly = true)
    public List<MatchCandidate> matches(Long id, SessionUser user) {
        return matchingService.topMatches(findOwned(id, user));
    }

    @Transactional(readOnly = true)
    public List<Detail> mine(SessionUser user, LostItemStatus status) {
        return lostItemRepository.findMine(user.loginId(), status).stream().map(Detail::from).toList();
    }

    @Transactional
    public Detail update(Long id, LostItemRequest request, SessionUser user) {
        LostItem item = lockOpen(id, user);
        validateNotFuture(request);
        item.update(findCategory(request.categoryId()), request.itemName().trim(), request.lostAt(),
                findBuilding(request.buildingId()), blankToNull(request.locationDetail()),
                blankToNull(request.description()));
        lostItemRepository.flush();
        return Detail.from(item);
    }

    /** 진행 중(PENDING·APPROVED) 신청이 연결돼 있으면 409. 지울 수 있으면 남은 신청의 연결을 끊고 삭제 */
    @Transactional
    public void delete(Long id, SessionUser user) {
        LostItem item = lockOpen(id, user);
        if (claimRequestRepository.existsByLostItemAndStatusIn(id, ONGOING)) {
            throw new BusinessException(ErrorCode.LOST_ITEM_HAS_CLAIM);
        }
        claimRequestRepository.unlinkLostItem(id);
        lostItemRepository.deleteById(item.getId());
    }

    /** "찾았음": 서비스 밖에서 찾음 → CLOSED. 연결된 신청은 자동 취소하지 않는다 (명세 기본안) */
    @Transactional
    public Detail close(Long id, SessionUser user) {
        LostItem item = lockOpen(id, user);
        item.close();
        lostItemRepository.flush();
        return Detail.from(item);
    }

    /** 없으면 404 → 남의 것이면 403 */
    private LostItem findOwned(Long id, SessionUser user) {
        LostItem item = lostItemRepository.findWithDetailsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!item.isOwnedBy(user.loginId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return item;
    }

    /** 행을 잠근 뒤: 없으면 404 → 남의 것이면 403 → CLOSED면 409 LOST_ITEM_CLOSED */
    private LostItem lockOpen(Long id, SessionUser user) {
        LostItem item = lostItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!item.isOwnedBy(user.loginId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (item.getStatus() == LostItemStatus.CLOSED) {
            throw new BusinessException(ErrorCode.LOST_ITEM_CLOSED);
        }
        return item;
    }

    private void validateNotFuture(LostItemRequest request) {
        if (request.lostAt().isAfter(timeProvider.now().plusMinutes(1))) {
            throw new BusinessException(ErrorCode.FUTURE_DATE);
        }
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "categoryId: 없는 카테고리입니다."));
    }

    private Building findBuilding(Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "buildingId: 없는 건물입니다."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
