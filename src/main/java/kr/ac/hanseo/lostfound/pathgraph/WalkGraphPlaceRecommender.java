package kr.ac.hanseo.lostfound.pathgraph;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.pathgraph.PlaceRecommendation.Method;
import lombok.RequiredArgsConstructor;

/**
 * 보행 그래프 방식 (FR-07 C안).
 * 1. 두 사람 위치를 가장 가까운 노드에 맞추고, 맞추는 거리는 1.25m/s로 시간에 더한다
 * 2. 미리 계산한 WALK_TIME을 두 노드로 한 번 조회
 * 3. max(A시간, B시간)이 가장 작은 건물, 같으면 합이 작은 건물, 그래도 같으면 buildingId가 작은 건물
 * WALK_TIME이 비어 있거나 두 사람이 함께 닿는 건물이 없으면 empty → 직선거리로 대체된다.
 */
@Component
@RequiredArgsConstructor
public class WalkGraphPlaceRecommender implements PlaceRecommender {

    private final PathNodeRepository nodeRepository;
    private final WalkTimeRepository walkTimeRepository;
    private final BuildingRepository buildingRepository;

    @Override
    public Optional<PlaceRecommendation> recommend(GeoPoint finder, GeoPoint owner) {
        List<PathNode> nodes = nodeRepository.findAll();
        if (nodes.isEmpty()) {
            return Optional.empty();
        }
        Snap f = snap(finder, nodes);
        Snap o = snap(owner, nodes);

        Map<Long, Integer> fromFinder = new HashMap<>();
        Map<Long, Integer> fromOwner = new HashMap<>();
        for (WalkTime w : walkTimeRepository.findFrom(
                // 두 사람이 같은 노드에 맞춰질 수 있으므로 중복을 허용하는 HashSet
                new java.util.HashSet<>(List.of(f.node().getId(), o.node().getId())))) {
            if (w.getFromNodeId().equals(f.node().getId())) {
                fromFinder.put(w.getToBuildingId(), w.getSeconds() + f.seconds());
            }
            if (w.getFromNodeId().equals(o.node().getId())) {
                fromOwner.put(w.getToBuildingId(), w.getSeconds() + o.seconds());
            }
        }

        record Scored(Long buildingId, int finder, int owner) {
            int max() {
                return Math.max(finder, owner);
            }
        }
        return fromFinder.keySet().stream()
                .filter(fromOwner::containsKey)
                .map(id -> new Scored(id, fromFinder.get(id), fromOwner.get(id)))
                .min(Comparator.comparingInt(Scored::max)
                        .thenComparingInt(s -> s.finder() + s.owner())
                        .thenComparing(Scored::buildingId))
                .flatMap(best -> buildingRepository.findById(best.buildingId())
                        .map(b -> new PlaceRecommendation(b, best.finder(), best.owner(), Method.WALK_GRAPH)));
    }

    /** 시연 모드: 고른 건물의 출입구(node_id가 가장 작은 것) 위치. 그래프에서 닿는 건물만 */
    @Override
    public Optional<GeoPoint> locationOf(Long buildingId) {
        if (!candidateBuildingIds().contains(buildingId)) {
            return Optional.empty();
        }
        return nodeRepository.findEntrancesOf(buildingId).stream().findFirst()
                .map(n -> GeoPoint.of(n.getLat(), n.getLng()));
    }

    /** WALK_TIME에 한 번이라도 나오는 건물 (출입구가 있고 길이 이어진 건물) */
    @Override
    public Set<Long> candidateBuildingIds() {
        return walkTimeRepository.findReachableBuildingIds();
    }

    private record Snap(PathNode node, int seconds) {
    }

    private static Snap snap(GeoPoint p, List<PathNode> nodes) {
        PathNode nearest = null;
        double best = Double.MAX_VALUE;
        for (PathNode n : nodes) {
            double d = p.distanceTo(GeoPoint.of(n.getLat(), n.getLng()));
            if (d < best) {
                best = d;
                nearest = n;
            }
        }
        return new Snap(nearest, (int) Math.round(best / WalkingSpeedModel.SNAP_SPEED_MPS));
    }
}
