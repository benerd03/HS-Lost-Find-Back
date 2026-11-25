package kr.ac.hanseo.lostfound.pathgraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 메모리 위의 방향 그래프. 간선 1개(양방향 길)는 방향별로 시간이 다른 두 간선이 된다.
 * 최단 시간은 Dijkstra로 계산한다 (가중치가 모두 양수이므로).
 */
public final class PathGraph {

    /** 노드 i의 코드, 소속 건물 ID(출입구가 아니면 null) */
    private final List<String> codes = new ArrayList<>();
    private final List<Long> buildingOf = new ArrayList<>();
    private final Map<String, Integer> indexOf = new HashMap<>();
    private final List<List<int[]>> adjacency = new ArrayList<>();

    public int addNode(String code, Long entranceOfBuildingId) {
        int index = codes.size();
        codes.add(code);
        buildingOf.add(entranceOfBuildingId);
        indexOf.put(code, index);
        adjacency.add(new ArrayList<>());
        return index;
    }

    /** 한 방향 간선 (from → to, 초) */
    public void addArc(int from, int to, int seconds) {
        adjacency.get(from).add(new int[] {to, seconds});
    }

    public int size() {
        return codes.size();
    }

    public String code(int index) {
        return codes.get(index);
    }

    public Integer index(String code) {
        return indexOf.get(code);
    }

    public Long entranceOf(int index) {
        return buildingOf.get(index);
    }

    /** 한 출발 노드에서 모든 노드까지의 최단 시간. 닿을 수 없으면 Integer.MAX_VALUE */
    public ShortestPaths dijkstra(int source) {
        int[] dist = new int[size()];
        int[] prev = new int[size()];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(prev, -1);
        dist[source] = 0;
        PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
        queue.add(new int[] {source, 0});
        while (!queue.isEmpty()) {
            int[] top = queue.poll();
            int u = top[0];
            if (top[1] > dist[u]) {
                continue;
            }
            for (int[] arc : adjacency.get(u)) {
                int v = arc[0];
                int candidate = dist[u] + arc[1];
                if (candidate < dist[v]) {
                    dist[v] = candidate;
                    prev[v] = u;
                    queue.add(new int[] {v, candidate});
                }
            }
        }
        return new ShortestPaths(dist, prev);
    }

    /** 노드 → 건물별 최단 시간 (건물의 여러 출입구 중 가장 빠른 곳). 닿을 수 없는 건물은 빠진다 */
    public Map<Long, Integer> timesToBuildings(int source) {
        int[] dist = dijkstra(source).dist();
        Map<Long, Integer> best = new HashMap<>();
        for (int i = 0; i < size(); i++) {
            Long building = buildingOf.get(i);
            if (building != null && dist[i] != Integer.MAX_VALUE) {
                best.merge(building, dist[i], Math::min);
            }
        }
        return best;
    }

    public record ShortestPaths(int[] dist, int[] prev) {

        /** source에서 target까지 지나는 노드 순서 */
        public List<Integer> pathTo(int target) {
            LinkedList<Integer> path = new LinkedList<>();
            if (dist[target] == Integer.MAX_VALUE) {
                return path;
            }
            for (int at = target; at != -1; at = prev[at]) {
                path.addFirst(at);
            }
            return path;
        }
    }
}
