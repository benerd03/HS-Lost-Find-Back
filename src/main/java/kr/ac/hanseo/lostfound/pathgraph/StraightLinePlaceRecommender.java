package kr.ac.hanseo.lostfound.pathgraph;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import kr.ac.hanseo.lostfound.catalog.Building;
import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.pathgraph.PlaceRecommendation.Method;
import lombok.RequiredArgsConstructor;

/**
 * 기획안 6장의 직선거리 방식. 좌표가 있는 건물("기타" 제외)마다 두 사람까지의 직선거리를 재고
 * 더 먼 쪽 거리가 가장 짧은 건물을 고른다. 아래 MySQL 쿼리와 같은 결과를 Java로 계산한다
 * (테스트용 H2에는 ST_Distance_Sphere가 없어서).
 *
 * <pre>
 * SELECT building_id FROM building WHERE lat IS NOT NULL
 * ORDER BY GREATEST(ST_Distance_Sphere(POINT(lng, lat), POINT(:finderLng, :finderLat)),
 *                   ST_Distance_Sphere(POINT(lng, lat), POINT(:ownerLng, :ownerLat))) LIMIT 1;
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class StraightLinePlaceRecommender implements PlaceRecommender {

    private final BuildingRepository buildingRepository;

    @Override
    public Optional<PlaceRecommendation> recommend(GeoPoint finder, GeoPoint owner) {
        record Scored(Building building, double max, double sum) {
        }
        return candidates().stream()
                .map(b -> {
                    GeoPoint p = GeoPoint.of(b.getLat(), b.getLng());
                    double f = p.distanceTo(finder);
                    double o = p.distanceTo(owner);
                    return new Scored(b, Math.max(f, o), f + o);
                })
                .min(Comparator.comparingDouble(Scored::max)
                        .thenComparingDouble(Scored::sum)
                        .thenComparing(s -> s.building().getId()))
                .map(s -> new PlaceRecommendation(s.building(), null, null, Method.STRAIGHT_LINE));
    }

    @Override
    public Optional<GeoPoint> locationOf(Long buildingId) {
        return buildingRepository.findById(buildingId)
                .filter(Building::hasCoordinate)
                .map(b -> GeoPoint.of(b.getLat(), b.getLng()));
    }

    @Override
    public Set<Long> candidateBuildingIds() {
        return candidates().stream().map(Building::getId).collect(Collectors.toSet());
    }

    private List<Building> candidates() {
        return buildingRepository.findAll().stream().filter(Building::hasCoordinate).toList();
    }
}
