package kr.ac.hanseo.lostfound.founditem;

import java.time.LocalDateTime;

import kr.ac.hanseo.lostfound.claim.ClaimStatus;

/** 습득물 API 응답 모양 (명세 "습득물" 절) */
public final class FoundItemResponses {

    private FoundItemResponses() {
    }

    /** 목록·검색 항목 */
    public record Summary(
            Long foundItemId,
            String itemName,
            String categoryName,
            String buildingName,
            String locationDetail,
            LocalDateTime foundAt,
            String imageUrl,
            FoundItemStatus status) {

        public static Summary from(FoundItem item) {
            return new Summary(item.getId(), item.getItemName(), item.getCategory().getName(),
                    item.getBuilding().getName(), item.getLocationDetail(), item.getFoundAt(),
                    item.getImagePath(), item.getStatus());
        }
    }

    /** 상세: 목록 항목 + categoryId, buildingId, description, createdAt, isMine, myClaimStatus, claimEligibility */
    public record Detail(
            Long foundItemId,
            String itemName,
            Long categoryId,
            String categoryName,
            Long buildingId,
            String buildingName,
            String locationDetail,
            LocalDateTime foundAt,
            String description,
            String imageUrl,
            FoundItemStatus status,
            LocalDateTime createdAt,
            boolean isMine,
            ClaimStatus myClaimStatus,
            ClaimEligibility claimEligibility) {

        public static Detail of(FoundItem item, boolean mine, ClaimStatus myClaimStatus) {
            return new Detail(item.getId(), item.getItemName(), item.getCategory().getId(),
                    item.getCategory().getName(), item.getBuilding().getId(), item.getBuilding().getName(),
                    item.getLocationDetail(), item.getFoundAt(), item.getDescription(), item.getImagePath(),
                    item.getStatus(), item.getCreatedAt(), mine, myClaimStatus,
                    ClaimEligibility.of(mine, myClaimStatus, item.getStatus()));
        }
    }

    public record Created(Long foundItemId) {
    }
}
