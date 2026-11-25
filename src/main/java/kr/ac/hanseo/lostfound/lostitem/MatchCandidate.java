package kr.ac.hanseo.lostfound.lostitem;

import java.time.LocalDateTime;
import java.util.List;

import kr.ac.hanseo.lostfound.founditem.ClaimEligibility;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;

/** 매칭 후보 1건. score는 항상 reasons의 points 합이다 */
public record MatchCandidate(
        Long foundItemId,
        String itemName,
        Long categoryId,
        String categoryName,
        String buildingName,
        String locationDetail,
        LocalDateTime foundAt,
        String imageUrl,
        FoundItemStatus status,
        ClaimEligibility claimEligibility,
        int score,
        List<Reason> reasons) {

    public record Reason(String code, String label, int points) {
    }
}
