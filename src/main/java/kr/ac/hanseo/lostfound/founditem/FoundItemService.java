package kr.ac.hanseo.lostfound.founditem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import kr.ac.hanseo.lostfound.catalog.Building;
import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.catalog.Category;
import kr.ac.hanseo.lostfound.catalog.CategoryRepository;
import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.common.file.ImageStorage;
import kr.ac.hanseo.lostfound.common.time.TimeProvider;
import kr.ac.hanseo.lostfound.common.tx.AfterTransaction;
import kr.ac.hanseo.lostfound.founditem.FoundItemResponses.Detail;
import kr.ac.hanseo.lostfound.founditem.FoundItemResponses.Summary;
import kr.ac.hanseo.lostfound.user.Student;
import kr.ac.hanseo.lostfound.user.StudentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FoundItemService {

    private final FoundItemRepository foundItemRepository;
    private final CategoryRepository categoryRepository;
    private final BuildingRepository buildingRepository;
    private final StudentRepository studentRepository;
    private final ClaimRequestRepository claimRequestRepository;
    private final ImageStorage imageStorage;
    private final TimeProvider timeProvider;

    /** 등록. 이미지를 먼저 저장하고, DB 저장이 실패하면 저장한 이미지를 지운다 */
    @Transactional
    public Long create(FoundItemForm form, MultipartFile image, SessionUser user) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_REQUIRED);
        }
        validateNotFuture(form);
        Category category = findCategory(form.categoryId());
        Building building = findBuilding(form.buildingId());
        Student finder = studentRepository.getReferenceById(user.loginId());

        String imageUrl = imageStorage.store(image);
        AfterTransaction.onRollback(() -> imageStorage.delete(imageUrl));

        FoundItem item = new FoundItem(finder, category, form.itemName().trim(), form.foundAt(), building,
                blankToNull(form.locationDetail()), blankToNull(form.description()), imageUrl);
        return foundItemRepository.save(item).getId();
    }

    @Transactional(readOnly = true)
    public Page<Summary> search(Long buildingId, Long categoryId, String keyword, Pageable pageable) {
        return foundItemRepository.findAll(FoundItemSearch.of(buildingId, categoryId, keyword), pageable)
                .map(Summary::from);
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id, SessionUser user) {
        FoundItem item = foundItemRepository.findActiveById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        boolean mine = user.isStudent() && item.isFoundBy(user.loginId());
        ClaimStatus myClaimStatus = user.isStudent()
                ? claimRequestRepository.findMine(id, user.loginId()).map(ClaimRequest::getStatus).orElse(null)
                : null;
        return Detail.of(item, mine, myClaimStatus);
    }

    /** 수정: 본인, STORED일 때만. 이미지는 보냈을 때만 바꾸고 옛 파일은 커밋 후 지운다 */
    @Transactional
    public void update(Long id, FoundItemForm form, MultipartFile image, SessionUser user) {
        FoundItem item = lockEditable(id, user);
        validateNotFuture(form);
        Category category = findCategory(form.categoryId());
        Building building = findBuilding(form.buildingId());

        item.update(category, form.itemName().trim(), form.foundAt(), building,
                blankToNull(form.locationDetail()), blankToNull(form.description()));

        if (image != null && !image.isEmpty()) {
            String oldUrl = item.getImagePath();
            String newUrl = imageStorage.store(image);
            AfterTransaction.onRollback(() -> imageStorage.delete(newUrl));
            AfterTransaction.onCommit(() -> imageStorage.delete(oldUrl));
            item.changeImage(newUrl);
        }
    }

    /** 삭제: 소프트 삭제 + 이미지 파일은 커밋 후 지움 */
    @Transactional
    public void delete(Long id, SessionUser user) {
        FoundItem item = lockEditable(id, user);
        item.softDelete(timeProvider.now());
        String imageUrl = item.getImagePath();
        AfterTransaction.onCommit(() -> imageStorage.delete(imageUrl));
    }

    /** 행을 잠근 뒤 확인: 없으면 404 → 남의 것이면 403 → STORED가 아니면 409 ITEM_LOCKED */
    private FoundItem lockEditable(Long id, SessionUser user) {
        FoundItem item = foundItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!item.isFoundBy(user.loginId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (item.getStatus() != FoundItemStatus.STORED) {
            throw new BusinessException(ErrorCode.ITEM_LOCKED);
        }
        return item;
    }

    private void validateNotFuture(FoundItemForm form) {
        if (form.foundAt().isAfter(timeProvider.now().plusMinutes(1))) {
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
