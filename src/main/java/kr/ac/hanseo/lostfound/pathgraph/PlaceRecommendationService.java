package kr.ac.hanseo.lostfound.pathgraph;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 장소 추천 창구. 보행 그래프(WALK_TIME) 방식이 준비되면 그것을 쓰고, 아니면 직선거리로 대체한다.
 * 지금은 보행 그래프 계산이 아직 구현되지 않아 항상 직선거리다.
 * 보행 그래프를 붙일 때: PlaceRecommender를 구현한 WalkGraphPlaceRecommender를 만들고
 * 아래 graph() 자리에 넣는다. 데이터가 없으면 recommend()가 empty를 돌려주게 하면 자동으로 대체된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationService {

    private final StraightLinePlaceRecommender straightLine;

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

    private Optional<PlaceRecommender> graph() {
        return Optional.empty();
    }
}
