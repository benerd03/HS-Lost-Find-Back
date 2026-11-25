package kr.ac.hanseo.lostfound.catalog;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** lat·lng은 표시용·직선거리 대체 계산용. "기타"는 NULL */
@Entity
@Table(name = "building")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "building_id")
    private Long id;

    @Column(name = "building_code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Building(String code, String name, BigDecimal lat, BigDecimal lng, int sortOrder) {
        this.code = code;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.sortOrder = sortOrder;
    }

    /** data/buildings.csv 내용으로 갱신 (코드가 같은 건물) */
    public void update(String name, BigDecimal lat, BigDecimal lng, int sortOrder) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.sortOrder = sortOrder;
    }

    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }
}
