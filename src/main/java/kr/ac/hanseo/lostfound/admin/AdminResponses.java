package kr.ac.hanseo.lostfound.admin;

import java.time.LocalDateTime;
import java.util.List;

import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemResponses;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.handover.HandoverStatus;
import kr.ac.hanseo.lostfound.lostitem.LostItem;

/** 관리자 API 응답 모양 (명세 "관리자" 절) */
public final class AdminResponses {

    private AdminResponses() {
    }

    /** A2 목록 항목 = 학생용 목록 항목 + createdAt + claimCount(모든 상태의 신청 수) */
    public record FoundItemRow(Long foundItemId, String itemName, String categoryName, String buildingName,
            String locationDetail, LocalDateTime foundAt, String imageUrl, FoundItemStatus status,
            LocalDateTime createdAt, long claimCount) {

        static FoundItemRow of(FoundItem f, long claimCount) {
            return new FoundItemRow(f.getId(), f.getItemName(), f.getCategory().getName(),
                    f.getBuilding().getName(), f.getLocationDetail(), f.getFoundAt(), f.getImagePath(),
                    f.getStatus(), f.getCreatedAt(), claimCount);
        }
    }

    /** A2 상세 = 목록 항목 + description + 습득자 */
    public record FoundItemDetail(Long foundItemId, String itemName, String categoryName, String buildingName,
            String locationDetail, LocalDateTime foundAt, String imageUrl, FoundItemStatus status,
            LocalDateTime createdAt, long claimCount, String description, Person finder) {

        static FoundItemDetail of(FoundItem f, long claimCount) {
            return new FoundItemDetail(f.getId(), f.getItemName(), f.getCategory().getName(),
                    f.getBuilding().getName(), f.getLocationDetail(), f.getFoundAt(), f.getImagePath(),
                    f.getStatus(), f.getCreatedAt(), claimCount, f.getDescription(),
                    new Person(f.getFinder().getStudentId(), f.getFinder().getName()));
        }
    }

    public record Person(String studentId, String name) {
    }

    /** A3 습득물별 신청 묶음 */
    public record ClaimBundle(FoundItemResponses.Summary item, Long handoverId, List<ClaimRow> claims) {
    }

    public record ClaimRow(Long claimId, ClaimStatus status, LocalDateTime requestedAt, Applicant student,
            String ownershipEvidence, LinkedLostItem lostItem, LocalDateTime processedAt) {

        static ClaimRow from(ClaimRequest c) {
            LostItem l = c.getLostItem();
            return new ClaimRow(c.getId(), c.getStatus(), c.getRequestedAt(),
                    new Applicant(c.getStudent().getStudentId(), c.getStudent().getName(), c.getStudent().getPhone()),
                    c.getOwnershipEvidence(),
                    l == null ? null : new LinkedLostItem(l.getId(), l.getItemName(), l.getLostAt(),
                            l.getBuilding().getName()),
                    c.getProcessedAt());
        }
    }

    /** 신청자 연락처는 관리자가 증빙을 확인할 때만 쓴다 */
    public record Applicant(String studentId, String name, String phone) {
    }

    public record LinkedLostItem(Long lostItemId, String itemName, LocalDateTime lostAt, String buildingName) {
    }

    /** 주인 확정 응답 */
    public record ApproveResult(Long claimId, ClaimStatus claimStatus, FoundItemStatus itemStatus,
            List<Long> rejectedClaimIds, HandoverRef handover, Long closedLostItemId) {
    }

    public record HandoverRef(Long handoverId, HandoverStatus status) {
    }

    /** 거절 응답 */
    public record RejectResult(Long claimId, ClaimStatus claimStatus, FoundItemStatus itemStatus) {
    }
}
