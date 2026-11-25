package kr.ac.hanseo.lostfound.pathgraph;

import java.math.BigDecimal;

/** 위도·경도 한 점 */
public record GeoPoint(double lat, double lng) {

    private static final double EARTH_RADIUS_M = 6_371_008.8;

    public static GeoPoint of(BigDecimal lat, BigDecimal lng) {
        return new GeoPoint(lat.doubleValue(), lng.doubleValue());
    }

    /** 두 점 사이 지표면 거리(m). MySQL ST_Distance_Sphere와 같은 하버사인 공식 */
    public double distanceTo(GeoPoint other) {
        double dLat = Math.toRadians(other.lat - lat);
        double dLng = Math.toRadians(other.lng - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
