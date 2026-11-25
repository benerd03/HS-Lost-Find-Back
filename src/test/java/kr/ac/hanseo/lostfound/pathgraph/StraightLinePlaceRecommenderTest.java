package kr.ac.hanseo.lostfound.pathgraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import kr.ac.hanseo.lostfound.catalog.Building;
import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.pathgraph.PlaceRecommendation.Method;

/** 직선거리 추천의 공정성 규칙 (minimax) */
class StraightLinePlaceRecommenderTest {

    // 위도 36.69 위에서 경도 0.001 ≈ 89m. 서→동으로 W, M, E 세 건물
    private final Building west = building(1L, "W", 36.69, 126.578);
    private final Building middle = building(2L, "M", 36.69, 126.580);
    private final Building east = building(3L, "E", 36.69, 126.582);
    private final Building etc = building(4L, "기타", null, null);

    private final StraightLinePlaceRecommender recommender = recommender(west, middle, east, etc);

    @Test
    void 양쪽_끝에_있으면_가운데_건물() {
        var result = recommender.recommend(new GeoPoint(36.69, 126.578), new GeoPoint(36.69, 126.582));

        assertThat(result).get().extracting(PlaceRecommendation::building).isEqualTo(middle);
        assertThat(result.get().method()).isEqualTo(Method.STRAIGHT_LINE);
        assertThat(result.get().finderWalkSeconds()).isNull();
    }

    @Test
    void 한_사람이_멀리_있으면_그_사람_쪽_건물을_골라_더_먼_쪽의_거리를_줄인다() {
        // 습득자는 W에, 분실자는 캠퍼스 동쪽 밖 1km 지점
        var result = recommender.recommend(new GeoPoint(36.69, 126.578), new GeoPoint(36.69, 126.593));

        assertThat(result).get().extracting(PlaceRecommendation::building).isEqualTo(east);
    }

    @Test
    void 같은_건물에_있으면_그_건물() {
        var result = recommender.recommend(new GeoPoint(36.69, 126.578), new GeoPoint(36.69, 126.578));

        assertThat(result).get().extracting(PlaceRecommendation::building).isEqualTo(west);
    }

    @Test
    void 완전히_같은_점수면_ID가_작은_건물() {
        StraightLinePlaceRecommender twins = recommender(building(9L, "B9", 36.69, 126.58),
                building(5L, "B5", 36.69, 126.58));

        var result = twins.recommend(new GeoPoint(36.691, 126.58), new GeoPoint(36.689, 126.58));

        assertThat(result.get().building().getId()).isEqualTo(5L);
    }

    @Test
    void 좌표_없는_건물은_후보도_시연_위치도_아니다() {
        assertThat(recommender.candidateBuildingIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(recommender.locationOf(4L)).isEmpty();
        assertThat(recommender.locationOf(2L)).contains(new GeoPoint(36.69, 126.58));
    }

    @Test
    void 좌표_있는_건물이_하나도_없으면_추천하지_않는다() {
        assertThat(recommender(etc).recommend(new GeoPoint(36.69, 126.58), new GeoPoint(36.69, 126.58))).isEmpty();
    }

    @Test
    void 거리_계산은_하버사인() {
        double d = new GeoPoint(36.69, 126.578).distanceTo(new GeoPoint(36.69, 126.582));
        assertThat(d).isBetween(355.0, 360.0);
    }

    private static StraightLinePlaceRecommender recommender(Building... buildings) {
        BuildingRepository repository = mock(BuildingRepository.class);
        when(repository.findAll()).thenReturn(List.of(buildings));
        for (Building b : buildings) {
            when(repository.findById(b.getId())).thenReturn(java.util.Optional.of(b));
        }
        return new StraightLinePlaceRecommender(repository);
    }

    private static Building building(Long id, String name, Double lat, Double lng) {
        Building b = new Building(name, name, lat == null ? null : BigDecimal.valueOf(lat),
                lng == null ? null : BigDecimal.valueOf(lng), 0);
        ReflectionTestUtils.setField(b, "id", id);
        return b;
    }
}
