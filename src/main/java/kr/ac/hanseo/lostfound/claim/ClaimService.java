package kr.ac.hanseo.lostfound.claim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.common.time.TimeProvider;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemRepository;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.lostitem.LostItem;
import kr.ac.hanseo.lostfound.lostitem.LostItemRepository;
import kr.ac.hanseo.lostfound.lostitem.LostItemStatus;
import kr.ac.hanseo.lostfound.notification.LinkKind;
import kr.ac.hanseo.lostfound.notification.NotificationService;
import kr.ac.hanseo.lostfound.notification.NotificationType;
import kr.ac.hanseo.lostfound.user.StudentRepository;
import lombok.RequiredArgsConstructor;

/**
 * 수령 신청 생성·취소 (FR-05). 둘 다 습득물 행을 먼저 잠그고, 잠근 뒤 다시 읽은 상태로 판단한다.
 * 그래서 관리자 확정과 동시에 들어와도 순서대로 처리된다 (명세 "동시성").
 */
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRequestRepository claimRequestRepository;
    private final FoundItemRepository foundItemRepository;
    private final LostItemRepository lostItemRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;
    private final TimeProvider timeProvider;

    public record ClaimResult(Long claimId, ClaimStatus status) {
    }

    /**
     * 판정 순서 (습득물 상세의 claimEligibility와 같음):
     * 없음 404 → 내 물건 CLAIM_OWN_ITEM → 이미 신청(거절·취소 포함) CLAIM_DUPLICATE
     * → 수령대기·완료 CLAIM_CLOSED → 연결할 분실 신고가 내 OPEN 신고가 아님 LOST_ITEM_INVALID
     */
    @Transactional
    public ClaimResult create(Long foundItemId, ClaimCreateRequest request, SessionUser user) {
        FoundItem item = foundItemRepository.findByIdForUpdate(foundItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (item.isFoundBy(user.loginId())) {
            throw new BusinessException(ErrorCode.CLAIM_OWN_ITEM);
        }
        if (claimRequestRepository.findMine(foundItemId, user.loginId()).isPresent()) {
            throw new BusinessException(ErrorCode.CLAIM_DUPLICATE);
        }
        if (item.getStatus() != FoundItemStatus.STORED && item.getStatus() != FoundItemStatus.CLAIM_REQUESTED) {
            throw new BusinessException(ErrorCode.CLAIM_CLOSED);
        }
        LostItem lostItem = request.lostItemId() == null ? null : findLinkableLostItem(request.lostItemId(), user);

        ClaimRequest claim = claimRequestRepository.save(new ClaimRequest(item,
                studentRepository.getReferenceById(user.loginId()), lostItem, request.ownershipEvidence().trim()));
        if (item.getStatus() == FoundItemStatus.STORED) {
            item.changeStatus(FoundItemStatus.CLAIM_REQUESTED);
        }
        notificationService.notify(item.getFinder(), NotificationType.CLAIM_RECEIVED,
                item.getItemName() + "에 수령 신청이 들어왔어요", LinkKind.FOUND_ITEM, item.getId());
        return new ClaimResult(claim.getId(), claim.getStatus());
    }

    /** 신청자 본인, PENDING일 때만. 남은 PENDING이 없으면 습득물은 STORED로 돌아간다 */
    @Transactional
    public ClaimResult cancel(Long claimId, SessionUser user) {
        Long foundItemId = claimRequestRepository.findFoundItemIdOf(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // 잠금 순서: 습득물 행 → 신청 다시 읽기 (확정·거절과 같은 순서)
        FoundItem item = foundItemRepository.findByIdForUpdate(foundItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ClaimRequest claim = claimRequestRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!claim.isRequestedBy(user.loginId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_PENDING);
        }
        claim.cancel(timeProvider.now());
        claimRequestRepository.flush();
        if (item.getStatus() == FoundItemStatus.CLAIM_REQUESTED
                && !claimRequestRepository.existsByFoundItemAndStatus(item.getId(), ClaimStatus.PENDING)) {
            item.changeStatus(FoundItemStatus.STORED);
        }
        return new ClaimResult(claim.getId(), claim.getStatus());
    }

    private LostItem findLinkableLostItem(Long lostItemId, SessionUser user) {
        return lostItemRepository.findById(lostItemId)
                .filter(l -> l.isOwnedBy(user.loginId()) && l.getStatus() == LostItemStatus.OPEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOST_ITEM_INVALID));
    }
}
