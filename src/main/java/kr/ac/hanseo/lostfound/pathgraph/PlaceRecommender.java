package kr.ac.hanseo.lostfound.pathgraph;

import java.util.Optional;

/**
 * 두 사람 위치로 만날 건물 1곳을 고른다 (FR-07).
 * 공정성 기준은 모든 구현이 같다: 두 사람 중 더 오래(멀리) 가는 쪽이 가장 덜 가는 건물(minimax),
 * 같으면 두 사람 합이 작은 건물, 그래도 같으면 buildingId가 작은 건물.
 */
public interface PlaceRecommender {

    /** 이 방식으로 추천할 수 없으면(데이터 없음 등) empty */
    Optional<PlaceRecommendation> recommend(GeoPoint finder, GeoPoint owner);

    /** 시연 모드에서 고른 건물을 현재 위치로 바꾼다. 추천 후보가 아닌 건물이면 empty */
    Optional<GeoPoint> locationOf(Long buildingId);

    /** 이 방식에서 추천 후보가 될 수 있는 건물 ID (건물 목록의 demoSelectable) */
    java.util.Set<Long> candidateBuildingIds();
}
