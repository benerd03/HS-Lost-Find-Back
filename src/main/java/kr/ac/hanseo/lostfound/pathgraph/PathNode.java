package kr.ac.hanseo.lostfound.pathgraph;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kr.ac.hanseo.lostfound.catalog.Building;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 보행 그래프 노드: 건물 출입구(ENTRANCE) 또는 길 교차점(JUNCTION). docs/DATA_GUIDE.md 참고 */
@Entity
@Table(name = "path_node")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PathNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "node_id")
    private Long id;

    @Column(name = "node_code", nullable = false, unique = true, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "node_type", nullable = false, length = 10)
    private NodeType type;

    /** ENTRANCE면 필수 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lng;

    /** 선택. 비어 있으면 고도가 있는 노드 3개 이상으로 평면을 맞춰 추정한다 (docs/ERD.md) */
    @Column(name = "elevation_m", precision = 6, scale = 2)
    private BigDecimal elevationM;

    @Column(length = 100)
    private String memo;

    public PathNode(String code, NodeType type, Building building, BigDecimal lat, BigDecimal lng,
            BigDecimal elevationM, String memo) {
        this.code = code;
        this.type = type;
        this.building = building;
        this.lat = lat;
        this.lng = lng;
        this.elevationM = elevationM;
        this.memo = memo;
    }
}
