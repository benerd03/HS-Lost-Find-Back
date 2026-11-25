package kr.ac.hanseo.lostfound.pathgraph;

/**
 * 간선 한 방향의 도보 시간 계산 (docs/ERD.md "보행 그래프와 장소 추천").
 * 우선순위: 실측·지도앱 시간 > 고도로 계산 > 평지 속도.
 */
public final class WalkingSpeedModel {

    /** 고도를 모를 때 쓰는 평지 속도 (Tobler 공식의 경사 0 값과 같음, 약 5.04km/h) */
    public static final double FLAT_SPEED_MPS = 6.0 * Math.exp(-3.5 * 0.05) / 3.6;
    /** GPS 위치에서 가장 가까운 노드까지 걷는 속도. 직선거리라 실제보다 짧으므로 조금 느리게 잡는다 */
    public static final double SNAP_SPEED_MPS = 1.25;
    /** 계단: 수평 이동 속도, 오를 때·내려갈 때 수직 속도 */
    public static final double STAIRS_HORIZONTAL_MPS = 1.0;
    public static final double STAIRS_UP_MPS = 0.2;
    public static final double STAIRS_DOWN_MPS = 0.35;

    private WalkingSpeedModel() {
    }

    /**
     * @param distanceM 수평 거리(m)
     * @param rise      도착 고도 - 출발 고도(m). 모르면 null
     */
    public static int seconds(EdgeType type, double distanceM, Double rise) {
        double seconds;
        if (type == EdgeType.STAIRS) {
            seconds = distanceM / STAIRS_HORIZONTAL_MPS;
            if (rise != null) {
                seconds += rise > 0 ? rise / STAIRS_UP_MPS : -rise / STAIRS_DOWN_MPS;
            }
        } else if (rise == null || distanceM <= 0) {
            seconds = distanceM / FLAT_SPEED_MPS;
        } else {
            seconds = distanceM / toblerSpeed(rise / distanceM);
        }
        return Math.max(1, (int) Math.round(seconds));
    }

    /** Tobler 보행 공식: 속도(km/h) = 6 × e^(-3.5 × |경사 + 0.05|). 약간 내리막(-5%)일 때 가장 빠르다 */
    public static double toblerSpeed(double slope) {
        return 6.0 * Math.exp(-3.5 * Math.abs(slope + 0.05)) / 3.6;
    }
}
