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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보행 그래프 간선: 길 1개 = 1행 (양방향). 방향별 시간은 고도 차로 따로 계산한다.
 * forward/backwardSeconds가 있으면 계산값 대신 실측값을 쓴다.
 */
@Entity
@Table(name = "path_edge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PathEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "edge_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_node_id", nullable = false)
    private PathNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_node_id", nullable = false)
    private PathNode toNode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "edge_type", nullable = false, length = 10)
    private EdgeType type = EdgeType.PATH;

    /** NULL이면 두 좌표로 계산 */
    @Column(name = "distance_m", precision = 7, scale = 2)
    private BigDecimal distanceM;

    @Column(name = "forward_seconds")
    private Integer forwardSeconds;

    @Column(name = "backward_seconds")
    private Integer backwardSeconds;

    @Column(length = 100)
    private String memo;

    public PathEdge(PathNode fromNode, PathNode toNode, EdgeType type, BigDecimal distanceM,
            Integer forwardSeconds, Integer backwardSeconds, String memo) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.type = type;
        this.distanceM = distanceM;
        this.forwardSeconds = forwardSeconds;
        this.backwardSeconds = backwardSeconds;
        this.memo = memo;
    }
}
