package kr.ac.hanseo.lostfound.pathgraph;

import java.util.ArrayList;
import java.util.List;

/**
 * 고도를 비워 둔 노드의 고도를 추정한다.
 * 캠퍼스가 한 방향으로 꾸준히 높아지는 지형이라, 고도를 넣은 노드들로 평면
 * 고도 = a + b × 동쪽(m) + c × 북쪽(m) 을 최소제곱으로 맞춘다.
 * 고도를 넣은 노드가 3개 미만이거나 한 줄 위에 있어 평면을 정할 수 없으면 평균 고도로 채우고,
 * 하나도 없으면 추정하지 않는다(null → 평지 속도).
 */
public final class ElevationEstimator {

    private ElevationEstimator() {
    }

    public record Point(double lat, double lng, Double elevation) {
    }

    /** 입력과 같은 순서로, 넣은 값은 그대로·빈 값은 추정값으로 채운 고도 목록 */
    public static List<Double> fill(List<Point> points) {
        List<Point> known = points.stream().filter(p -> p.elevation() != null).toList();
        if (known.isEmpty()) {
            return points.stream().map(p -> (Double) null).toList();
        }
        double lat0 = known.get(0).lat();
        double lng0 = known.get(0).lng();
        double[] plane = known.size() >= 3 ? fitPlane(known, lat0, lng0) : null;
        double mean = known.stream().mapToDouble(Point::elevation).average().orElse(0);

        List<Double> result = new ArrayList<>(points.size());
        for (Point p : points) {
            if (p.elevation() != null) {
                result.add(p.elevation());
            } else if (plane != null) {
                double[] xy = toMeters(p, lat0, lng0);
                result.add(plane[0] + plane[1] * xy[0] + plane[2] * xy[1]);
            } else {
                result.add(mean);
            }
        }
        return result;
    }

    /** 정규방정식 (XᵀX)β = Xᵀy 를 3×3 가우스 소거로 푼다. 행렬이 거의 특이하면 null */
    private static double[] fitPlane(List<Point> known, double lat0, double lng0) {
        double[][] m = new double[3][4];
        for (Point p : known) {
            double[] xy = toMeters(p, lat0, lng0);
            double[] row = {1, xy[0], xy[1]};
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    m[i][j] += row[i] * row[j];
                }
                m[i][3] += row[i] * p.elevation();
            }
        }
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int r = col + 1; r < 3; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) {
                    pivot = r;
                }
            }
            if (Math.abs(m[pivot][col]) < 1e-6 * Math.max(1, m[0][0])) {
                return null;
            }
            double[] tmp = m[col];
            m[col] = m[pivot];
            m[pivot] = tmp;
            for (int r = 0; r < 3; r++) {
                if (r != col) {
                    double f = m[r][col] / m[col][col];
                    for (int c = col; c < 4; c++) {
                        m[r][c] -= f * m[col][c];
                    }
                }
            }
        }
        return new double[] {m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2]};
    }

    /** 기준점에서 동쪽·북쪽으로 몇 m인지 (캠퍼스 규모에서는 평면으로 봐도 충분) */
    private static double[] toMeters(Point p, double lat0, double lng0) {
        double north = (p.lat() - lat0) * 111_320.0;
        double east = (p.lng() - lng0) * 111_320.0 * Math.cos(Math.toRadians(lat0));
        return new double[] {east, north};
    }
}
