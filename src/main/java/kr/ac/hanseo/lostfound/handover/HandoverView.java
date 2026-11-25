package kr.ac.hanseo.lostfound.handover;

import java.time.LocalDateTime;

import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import kr.ac.hanseo.lostfound.pathgraph.PlaceRecommendation.Method;

/**
 * 전달 상태 조회 응답 (S10 패널이 5초마다 받음). ready·reset·receive 응답도 같은 모양.
 * 좌표와 상대의 이름·연락처는 보내지 않는다.
 */
public record HandoverView(
        Long handoverId,
        HandoverStatus status,
        HandoverRole myRole,
        LocalDateTime serverNow,
        Long claimId,
        ClaimStatus claimStatus,
        Item item,
        LocalDateTime finderReadyUntil,
        LocalDateTime ownerReadyUntil,
        boolean finderReady,
        boolean ownerReady,
        Place building,
        LocalDateTime placeSetAt,
        LocalDateTime completedAt) {

    public record Item(Long foundItemId, String itemName, Long categoryId, String categoryName, String buildingName,
            String imageUrl, FoundItemStatus status) {
    }

    /** 추천 건물. 도보 시간은 보행 그래프로 계산했을 때만 있다 */
    public record Place(Long buildingId, String name, Integer myWalkSeconds, Integer otherWalkSeconds,
            Method method) {
    }

    public static HandoverView of(Handover h, HandoverRole role, LocalDateTime now) {
        ClaimRequest claim = h.getClaim();
        FoundItem f = claim.getFoundItem();
        Place place = null;
        if (h.getBuilding() != null) {
            Integer mine = role == HandoverRole.FINDER ? h.getFinderWalkSeconds() : h.getOwnerWalkSeconds();
            Integer other = role == HandoverRole.FINDER ? h.getOwnerWalkSeconds() : h.getFinderWalkSeconds();
            Method method = h.getFinderWalkSeconds() == null ? Method.STRAIGHT_LINE : Method.WALK_GRAPH;
            place = new Place(h.getBuilding().getId(), h.getBuilding().getName(), mine, other, method);
        }
        return new HandoverView(h.getId(), h.getStatus(), role, now, claim.getId(), claim.getStatus(),
                new Item(f.getId(), f.getItemName(), f.getCategory().getId(), f.getCategory().getName(),
                        f.getBuilding().getName(), f.getImagePath(), f.getStatus()),
                h.getFinderReadyUntil(), h.getOwnerReadyUntil(), h.isFinderReady(now), h.isOwnerReady(now),
                place, h.getPlaceSetAt(), h.getCompletedAt());
    }
}
