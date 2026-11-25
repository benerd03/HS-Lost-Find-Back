package kr.ac.hanseo.lostfound.pathgraph;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 미리 계산한 최단 도보 시간: 노드 → 건물 (Dijkstra 결과) */
@Entity
@Table(name = "walk_time")
@IdClass(WalkTime.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalkTime {

    @Id
    @Column(name = "from_node_id")
    private Long fromNodeId;

    @Id
    @Column(name = "to_building_id")
    private Long toBuildingId;

    @Column(nullable = false)
    private int seconds;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    public WalkTime(Long fromNodeId, Long toBuildingId, int seconds, LocalDateTime computedAt) {
        this.fromNodeId = fromNodeId;
        this.toBuildingId = toBuildingId;
        this.seconds = seconds;
        this.computedAt = computedAt;
    }

    @Getter
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.EqualsAndHashCode
    public static class Key implements Serializable {
        private Long fromNodeId;
        private Long toBuildingId;
    }
}
