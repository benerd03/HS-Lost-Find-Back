package kr.ac.hanseo.lostfound.pathgraph;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 장소 추천 창구. 보행 그래프(WALK_TIME)가 계산돼 있으면 그것을 쓰고, 아니면 직선거리로 대체한다.
 * 그래프가 있어도 두 사람이 함께 닿는 건물이 없으면(길이 끊긴 경우 등) 직선거리로 대체한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationService {

    private final StraightLinePlaceRecommender straightLine;
    private final WalkGraphPlaceRecommender walkGraph;
    private final WalkTimeRepository walkTimeRepository;

    public Optional<PlaceRecommendation> recommend(GeoPoint finder, GeoPoint owner) {
        return graph().flatMap(g -> g.recommend(finder, owner)).or(() -> straightLine.recommend(finder, owner));
    }

    public Optional<GeoPoint> locationOf(Long buildingId) {
        return graph().flatMap(g -> g.locationOf(buildingId)).or(() -> straightLine.locationOf(buildingId));
    }

    /** 건물 목록의 demoSelectable */
    public Set<Long> selectableBuildingIds() {
        return graph().map(PlaceRecommender::candidateBuildingIds).filter(ids -> !ids.isEmpty())
                .orElseGet(straightLine::candidateBuildingIds);
    }

    /** WALK_TIME이 채워져 있을 때만 보행 그래프 방식 */
    private Optional<PlaceRecommender> graph() {
        return walkTimeRepository.count() > 0 ? Optional.of(walkGraph) : Optional.empty();
    }
}
