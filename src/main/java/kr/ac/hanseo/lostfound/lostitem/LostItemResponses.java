package kr.ac.hanseo.lostfound.lostitem;

import java.time.LocalDateTime;
import java.util.List;

/** 분실 신고 API 응답 모양 (명세 "분실 신고와 자동 매칭" 절) */
public final class LostItemResponses {

    private LostItemResponses() {
    }

    /** 상세, 내 분실 신고 목록 항목 */
    public record Detail(
            Long lostItemId,
            String itemName,
            Long categoryId,
            String categoryName,
            LocalDateTime lostAt,
            Long buildingId,
            String buildingName,
            String locationDetail,
            String description,
            LostItemStatus status,
            LocalDateTime createdAt) {

        public static Detail from(LostItem item) {
            return new Detail(item.getId(), item.getItemName(), item.getCategory().getId(),
                    item.getCategory().getName(), item.getLostAt(), item.getBuilding().getId(),
                    item.getBuilding().getName(), item.getLocationDetail(), item.getDescription(),
                    item.getStatus(), item.getCreatedAt());
        }
    }

    /** 등록 응답: 신고 번호 + 매칭 Top 3 */
    public record Created(Long lostItemId, List<MatchCandidate> matches) {
    }

    /** 후보 다시 조회 응답 */
    public record Matches(List<MatchCandidate> matches) {
    }
}
