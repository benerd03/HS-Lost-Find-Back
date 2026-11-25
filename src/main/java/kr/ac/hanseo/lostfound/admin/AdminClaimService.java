package kr.ac.hanseo.lostfound.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.hanseo.lostfound.admin.AdminResponses.ApproveResult;
import kr.ac.hanseo.lostfound.admin.AdminResponses.HandoverRef;
import kr.ac.hanseo.lostfound.admin.AdminResponses.RejectResult;
import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimRequestRepository;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.common.time.TimeProvider;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemRepository;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.handover.Handover;
import kr.ac.hanseo.lostfound.handover.HandoverRepository;
import kr.ac.hanseo.lostfound.lostitem.LostItem;
import kr.ac.hanseo.lostfound.lostitem.LostItemStatus;
import kr.ac.hanseo.lostfound.notification.LinkKind;
import kr.ac.hanseo.lostfound.notification.NotificationService;
import kr.ac.hanseo.lostfound.notification.NotificationType;
import kr.ac.hanseo.lostfound.user.Admin;
import kr.ac.hanseo.lostfound.user.AdminRepository;
import lombok.RequiredArgsConstructor;

/**
 * 주인 확정·거절 (FR-05, 기획안 "트랜잭션 규칙" 1·4).
 * 학생의 신청·취소와 같은 순서로 잠근다: 습득물 행(FOR UPDATE) → 신청 다시 읽기.
 * 하나라도 실패하면 예외로 전체가 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class AdminClaimService {

    static final String AUTO_REJECT_REASON = "다른 신청자가 주인으로 확정됨";

    private final ClaimRequestRepository claimRequestRepository;
    private final FoundItemRepository foundItemRepository;
    private final HandoverRepository handoverRepository;
    private final AdminRepository adminRepository;
    private final NotificationService notificationService;
    private final TimeProvider timeProvider;

    /**
     * 1. 습득물 행 잠금
     * 2. 이미 주인이 정해졌으면(수령대기·완료이거나 APPROVED 신청이 있음) OWNER_ALREADY_CONFIRMED
     * 3. 선택한 신청이 PENDING이 아니면 CLAIM_NOT_PENDING
     * 4. 선택한 신청 APPROVED, 나머지 PENDING은 REJECTED
     * 5. 습득물 AWAITING_PICKUP  6. 전달 WAITING 생성  7. 연결된 OPEN 분실 신고 CLOSED
     * 8. 알림: 확정자 CLAIM_APPROVED, 거절된 사람들 CLAIM_REJECTED, 습득자 HANDOVER_STARTED
     */
    @Transactional
    public ApproveResult approve(Long claimId, SessionUser adminUser) {
        FoundItem item = lockItemOf(claimId);
        if (item.getStatus() == FoundItemStatus.AWAITING_PICKUP || item.getStatus() == FoundItemStatus.CLAIMED
                || claimRequestRepository.existsByFoundItemAndStatus(item.getId(), ClaimStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.OWNER_ALREADY_CONFIRMED);
        }
        ClaimRequest claim = reloadPending(claimId);
        Admin admin = adminRepository.getReferenceById(adminUser.loginId());
        LocalDateTime now = timeProvider.now();
        String itemName = item.getItemName();

        claim.approve(admin, now);
        List<Long> rejectedIds = new ArrayList<>();
        for (ClaimRequest other : claimRequestRepository.findOtherPending(item.getId(), claimId)) {
            other.reject(admin, AUTO_REJECT_REASON, now);
            rejectedIds.add(other.getId());
            notificationService.notify(other.getStudent(), NotificationType.CLAIM_REJECTED,
                    itemName + " 수령 신청이 거절됐어요", LinkKind.CLAIM, other.getId());
        }
        item.changeStatus(FoundItemStatus.AWAITING_PICKUP);
        Handover handover = handoverRepository.save(new Handover(claim));

        Long closedLostItemId = null;
        LostItem lostItem = claim.getLostItem();
        if (lostItem != null && lostItem.getStatus() == LostItemStatus.OPEN) {
            lostItem.close();
            closedLostItemId = lostItem.getId();
        }

        notificationService.notify(claim.getStudent(), NotificationType.CLAIM_APPROVED,
                itemName + "의 주인으로 확정됐어요", LinkKind.HANDOVER, handover.getId());
        notificationService.notify(item.getFinder(), NotificationType.HANDOVER_STARTED,
                itemName + " 주인이 확정됐어요. 전달을 진행해 주세요", LinkKind.HANDOVER, handover.getId());

        return new ApproveResult(claim.getId(), claim.getStatus(), item.getStatus(), rejectedIds,
                new HandoverRef(handover.getId(), handover.getStatus()), closedLostItemId);
    }

    /** 거절. 같은 잠금 순서. 마지막 PENDING을 거절하면 습득물은 STORED로 돌아간다 */
    @Transactional
    public RejectResult reject(Long claimId, String reason, SessionUser adminUser) {
        FoundItem item = lockItemOf(claimId);
        ClaimRequest claim = reloadPending(claimId);
        claim.reject(adminRepository.getReferenceById(adminUser.loginId()),
                reason == null || reason.isBlank() ? null : reason.trim(), timeProvider.now());
        claimRequestRepository.flush();
        if (item.getStatus() == FoundItemStatus.CLAIM_REQUESTED
                && !claimRequestRepository.existsByFoundItemAndStatus(item.getId(), ClaimStatus.PENDING)) {
            item.changeStatus(FoundItemStatus.STORED);
        }
        notificationService.notify(claim.getStudent(), NotificationType.CLAIM_REJECTED,
                item.getItemName() + " 수령 신청이 거절됐어요", LinkKind.CLAIM, claim.getId());
        return new RejectResult(claim.getId(), claim.getStatus(), item.getStatus());
    }

    /** 신청 엔티티를 미리 읽지 않고 습득물 ID만 얻어 잠근다 (잠근 뒤 신청을 새로 읽기 위해) */
    private FoundItem lockItemOf(Long claimId) {
        Long foundItemId = claimRequestRepository.findFoundItemIdOf(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return foundItemRepository.findByIdForUpdate(foundItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private ClaimRequest reloadPending(Long claimId) {
        ClaimRequest claim = claimRequestRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_PENDING);
        }
        return claim;
    }
}
