package kr.ac.hanseo.lostfound.pathgraph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import kr.ac.hanseo.lostfound.pathgraph.ElevationEstimator.Point;

/** 간선 시간 계산, 고도 추정, Dijkstra */
class WalkingModelTest {

    @Test
    void 평지는_시속_5km쯤이다() {
        assertThat(WalkingSpeedModel.seconds(EdgeType.PATH, 140, null)).isEqualTo(100);
        assertThat(WalkingSpeedModel.seconds(EdgeType.PATH, 140, 0.0)).isEqualTo(100);
    }

    @Test
    void 같은_길도_오르막이_내리막보다_오래_걸린다() {
        int up = WalkingSpeedModel.seconds(EdgeType.PATH, 200, 20.0);
        int down = WalkingSpeedModel.seconds(EdgeType.PATH, 200, -20.0);
        assertThat(up).isEqualTo(203);
        assertThat(down).isEqualTo(143);
    }

    @Test
    void 완만한_내리막이_가장_빠르다() {
        assertThat(WalkingSpeedModel.toblerSpeed(-0.05)).isGreaterThan(WalkingSpeedModel.toblerSpeed(0));
        assertThat(WalkingSpeedModel.toblerSpeed(-0.05)).isGreaterThan(WalkingSpeedModel.toblerSpeed(-0.2));
    }

    @Test
    void 계단은_수평거리와_높이로_계산한다() {
        // 수평 10m + 6m 오르기 = 10 + 30초 / 내려가기 = 10 + 약 17초
        assertThat(WalkingSpeedModel.seconds(EdgeType.STAIRS, 10, 6.0)).isEqualTo(40);
        assertThat(WalkingSpeedModel.seconds(EdgeType.STAIRS, 10, -6.0)).isEqualTo(27);
    }

    @Test
    void 고도_3점으로_평면을_맞춰_빈_고도를_채운다() {
        // 북쪽으로 100m마다 10m 높아지는 평면 (1m ≈ 1/111320도)
        double m = 1.0 / 111_320;
        List<Double> filled = ElevationEstimator.fill(List.of(
                new Point(36.69, 126.58, 0.0),
                new Point(36.69 + 200 * m, 126.58, 20.0),
                new Point(36.69 + 200 * m, 126.581, 20.0),
                new Point(36.69 + 150 * m, 126.58, null)));
        assertThat(filled.get(3)).isCloseTo(15.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(filled.get(0)).isEqualTo(0.0);
    }

    @Test
    void 고도가_한_줄_위에만_있거나_3개_미만이면_평균으로_고도가_없으면_null() {
        List<Double> line = ElevationEstimator.fill(List.of(
                new Point(36.690, 126.58, 0.0), new Point(36.691, 126.58, 10.0),
                new Point(36.692, 126.58, 20.0), new Point(36.6905, 126.58, null)));
        assertThat(line.get(3)).isEqualTo(10.0);
        assertThat(ElevationEstimator.fill(List.of(new Point(36.69, 126.58, null))).get(0)).isNull();
    }

    @Test
    void 다익스트라는_방향별_시간으로_가장_빠른_출입구를_찾는다() {
        PathGraph g = new PathGraph();
        int a = g.addNode("A", null);
        int b = g.addNode("B", 1L);   // 건물1 정문
        int c = g.addNode("C", 1L);   // 건물1 후문
        int d = g.addNode("D", 2L);   // 건물2
        g.addArc(a, b, 100);
        g.addArc(b, a, 60);
        g.addArc(a, c, 40);
        g.addArc(c, a, 40);
        g.addArc(c, d, 30);

        assertThat(g.timesToBuildings(a)).containsEntry(1L, 40).containsEntry(2L, 70);
        assertThat(g.timesToBuildings(b)).containsEntry(1L, 0).containsEntry(2L, 130);
        assertThat(g.timesToBuildings(d)).containsOnlyKeys(2L); // D에서 나가는 길이 없어 자기 건물만
        assertThat(g.dijkstra(a).pathTo(d)).containsExactly(a, c, d);
    }
}
