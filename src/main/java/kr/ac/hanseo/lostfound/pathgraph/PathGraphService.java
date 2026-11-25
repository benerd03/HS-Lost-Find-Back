package kr.ac.hanseo.lostfound.pathgraph;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import kr.ac.hanseo.lostfound.catalog.Building;
import kr.ac.hanseo.lostfound.catalog.BuildingRepository;
import kr.ac.hanseo.lostfound.common.config.AppProperties;
import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.common.time.TimeProvider;
import kr.ac.hanseo.lostfound.pathgraph.GraphCsvReader.EdgeRow;
import kr.ac.hanseo.lostfound.pathgraph.GraphCsvReader.GraphData;
import kr.ac.hanseo.lostfound.pathgraph.GraphCsvReader.NodeRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 캠퍼스 보행 그래프 (FR-07 C안).
 * CSV → PATH_NODE·PATH_EDGE → 간선 시간(실측 > 고도 계산 > 평지) → 노드마다 Dijkstra → WALK_TIME
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PathGraphService {

    private final PathNodeRepository nodeRepository;
    private final PathEdgeRepository edgeRepository;
    private final WalkTimeRepository walkTimeRepository;
    private final BuildingRepository buildingRepository;
    private final EntityManager entityManager;
    private final AppProperties appProperties;
    private final TimeProvider timeProvider;

    public record GraphStatus(long nodeCount, long edgeCount, int candidateBuildingCount, LocalDateTime computedAt,
            List<String> unreachableNodes, List<String> buildingsWithoutEntrance) {
    }

    public record Route(int seconds, List<String> nodes) {
    }

    public boolean csvExists() {
        return GraphCsvReader.exists(dataDir());
    }

    /**
     * CSV를 다시 읽어 그래프를 통째로 바꾸고 WALK_TIME을 다시 계산한다.
     * 검증은 지우기 전에 끝내므로, CSV에 오류가 있으면 기존 그래프는 그대로 남는다.
     */
    @Transactional
    public GraphStatus rebuild() {
        if (!csvExists()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "data 폴더에 " + GraphCsvReader.NODES_FILE + ", " + GraphCsvReader.EDGES_FILE + "가 없습니다.");
        }
        Map<String, Building> buildings = new HashMap<>();
        buildingRepository.findAll().forEach(b -> buildings.put(b.getCode(), b));
        Map<String, Long> buildingIds = new HashMap<>();
        buildings.forEach((code, b) -> buildingIds.put(code, b.getId()));
        GraphData data = GraphCsvReader.read(dataDir(), buildingIds);

        walkTimeRepository.deleteAllInBatch();
        edgeRepository.deleteAllInBatch();
        nodeRepository.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();

        Map<String, PathNode> saved = new HashMap<>();
        for (NodeRow n : data.nodes()) {
            Building building = n.buildingCode() == null ? null
                    : entityManager.getReference(Building.class, buildingIds.get(n.buildingCode()));
            PathNode node = new PathNode(n.code(), n.type(), building, n.lat(), n.lng(), n.elevationM(), n.memo());
            entityManager.persist(node);
            saved.put(n.code(), node);
        }
        for (EdgeRow e : data.edges()) {
            entityManager.persist(new PathEdge(saved.get(e.fromCode()), saved.get(e.toCode()), e.type(),
                    e.distanceM(), e.forwardSeconds(), e.backwardSeconds(), e.memo()));
        }
        entityManager.flush();
        log.info("[path-graph] CSV에서 노드 {}개, 간선 {}개를 읽었습니다", data.nodes().size(), data.edges().size());
        return recompute();
    }

    /** DB의 노드·간선으로 WALK_TIME을 다시 채운다 */
    @Transactional
    public GraphStatus recompute() {
        List<PathNode> nodes = nodeRepository.findAllWithBuilding();
        PathGraph graph = buildGraph(nodes, edgeRepository.findAllWithNodes());

        walkTimeRepository.deleteAllInBatch();
        LocalDateTime now = timeProvider.now();
        int rows = 0;
        for (int i = 0; i < graph.size(); i++) {
            Long nodeId = nodes.get(i).getId();
            for (Map.Entry<Long, Integer> e : graph.timesToBuildings(i).entrySet()) {
                entityManager.persist(new WalkTime(nodeId, e.getKey(), e.getValue(), now));
                rows++;
            }
        }
        entityManager.flush();
        log.info("[path-graph] WALK_TIME {}행 계산 (노드 {}개)", rows, nodes.size());
        return status();
    }

    @Transactional(readOnly = true)
    public GraphStatus status() {
        List<PathNode> nodes = nodeRepository.findAllWithBuilding();
        Set<Long> entranceBuildings = nodeRepository.findBuildingIdsWithEntrance();
        Set<Long> reachable = walkTimeRepository.findReachableBuildingIds();

        // 출입구가 있는 건물 중 하나라도 닿지 못하는 노드
        Map<Long, Set<Long>> reachedBy = new HashMap<>();
        for (WalkTime w : walkTimeRepository.findAll()) {
            reachedBy.computeIfAbsent(w.getFromNodeId(), k -> new java.util.HashSet<>()).add(w.getToBuildingId());
        }
        List<String> unreachable = nodes.stream()
                .filter(n -> !reachedBy.getOrDefault(n.getId(), Set.of()).containsAll(entranceBuildings))
                .map(PathNode::getCode).sorted().toList();
        List<String> withoutEntrance = buildingRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(b -> !entranceBuildings.contains(b.getId())).map(Building::getName).toList();

        return new GraphStatus(nodes.size(), edgeRepository.count(), reachable.size(),
                walkTimeRepository.findComputedAt(), unreachable, withoutEntrance);
    }

    /** 노드 → 건물의 계산된 시간과 지나는 노드 (실측 보정용) */
    @Transactional(readOnly = true)
    public Route route(String fromNodeCode, String toBuildingCode) {
        List<PathNode> nodes = nodeRepository.findAllWithBuilding();
        PathGraph graph = buildGraph(nodes, edgeRepository.findAllWithNodes());
        Integer from = graph.index(fromNodeCode);
        Building building = buildingRepository.findByCode(toBuildingCode).orElse(null);
        if (from == null || building == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "노드 또는 건물 코드를 찾을 수 없습니다.");
        }
        PathGraph.ShortestPaths paths = graph.dijkstra(from);
        int best = -1;
        for (int i = 0; i < graph.size(); i++) {
            if (building.getId().equals(graph.entranceOf(i)) && paths.dist()[i] != Integer.MAX_VALUE
                    && (best == -1 || paths.dist()[i] < paths.dist()[best])) {
                best = i;
            }
        }
        if (best == -1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, toBuildingCode + "까지 이어진 길이 없습니다.");
        }
        return new Route(paths.dist()[best], paths.pathTo(best).stream().map(graph::code).toList());
    }

    /** 노드 순서는 nodes 목록 순서와 같다. 간선마다 두 방향 시간을 따로 계산한다 */
    static PathGraph buildGraph(List<PathNode> nodes, List<PathEdge> edges) {
        List<Double> elevations = ElevationEstimator.fill(nodes.stream()
                .map(n -> new ElevationEstimator.Point(n.getLat().doubleValue(), n.getLng().doubleValue(),
                        n.getElevationM() == null ? null : n.getElevationM().doubleValue()))
                .toList());
        PathGraph graph = new PathGraph();
        Map<Long, Integer> indexById = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            PathNode n = nodes.get(i);
            graph.addNode(n.getCode(), n.getBuilding() == null ? null : n.getBuilding().getId());
            indexById.put(n.getId(), i);
        }
        for (PathEdge e : edges) {
            int a = indexById.get(e.getFromNode().getId());
            int b = indexById.get(e.getToNode().getId());
            double distance = e.getDistanceM() != null ? e.getDistanceM().doubleValue()
                    : GeoPoint.of(e.getFromNode().getLat(), e.getFromNode().getLng())
                            .distanceTo(GeoPoint.of(e.getToNode().getLat(), e.getToNode().getLng()));
            Double rise = elevations.get(a) == null || elevations.get(b) == null ? null
                    : elevations.get(b) - elevations.get(a);
            int forward = e.getForwardSeconds() != null ? e.getForwardSeconds()
                    : WalkingSpeedModel.seconds(e.getType(), distance, rise);
            int backward = e.getBackwardSeconds() != null ? e.getBackwardSeconds()
                    : WalkingSpeedModel.seconds(e.getType(), distance, rise == null ? null : -rise);
            graph.addArc(a, b, forward);
            graph.addArc(b, a, backward);
        }
        return graph;
    }

    private Path dataDir() {
        return Path.of(appProperties.dataDir());
    }
}
