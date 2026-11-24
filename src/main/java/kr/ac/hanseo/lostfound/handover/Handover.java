package kr.ac.hanseo.lostfound.handover;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import kr.ac.hanseo.lostfound.catalog.Building;
import kr.ac.hanseo.lostfound.claim.ClaimRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전달 진행 (FR-07). 주인 확정 때 1행 생성.
 * 습득자 = claim.foundItem.finder, 분실자 = claim.student
 * 임시 좌표(*Lat, *Lng)는 건물 계산이 끝나거나 ready가 만료되면 지운다.
 * ready·reset·receive는 HandoverRepository.findByIdForUpdate로 행을 잠근 뒤 처리한다.
 */
@Entity
@Table(name = "handover")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "handover_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, unique = true)
    private ClaimRequest claim;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private HandoverStatus status = HandoverStatus.WAITING;

    @Column(name = "finder_ready_until")
    private LocalDateTime finderReadyUntil;

    @Column(name = "owner_ready_until")
    private LocalDateTime ownerReadyUntil;

    @Column(name = "finder_lat", precision = 9, scale = 6)
    private BigDecimal finderLat;

    @Column(name = "finder_lng", precision = 9, scale = 6)
    private BigDecimal finderLng;

    @Column(name = "owner_lat", precision = 9, scale = 6)
    private BigDecimal ownerLat;

    @Column(name = "owner_lng", precision = 9, scale = 6)
    private BigDecimal ownerLng;

    /** 추천 건물 (PLACE_SET 이후) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(name = "finder_walk_seconds")
    private Integer finderWalkSeconds;

    @Column(name = "owner_walk_seconds")
    private Integer ownerWalkSeconds;

    /** 주인 확정 시각 (A4 날짜 필터 기준) */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "place_set_at")
    private LocalDateTime placeSetAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Handover(ClaimRequest claim) {
        this.claim = claim;
    }

    public boolean isFinderReady(LocalDateTime now) {
        return finderReadyUntil != null && finderReadyUntil.isAfter(now);
    }

    public boolean isOwnerReady(LocalDateTime now) {
        return ownerReadyUntil != null && ownerReadyUntil.isAfter(now);
    }

    public void markFinderReady(LocalDateTime until, BigDecimal lat, BigDecimal lng) {
        this.finderReadyUntil = until;
        this.finderLat = lat;
        this.finderLng = lng;
    }

    public void markOwnerReady(LocalDateTime until, BigDecimal lat, BigDecimal lng) {
        this.ownerReadyUntil = until;
        this.ownerLat = lat;
        this.ownerLng = lng;
    }

    /** 만료된 사람의 임시 좌표 삭제 (명세: 30분이 지나면 다음 조회 때 지움) */
    public void clearExpiredCoordinates(LocalDateTime now) {
        if (!isFinderReady(now)) {
            finderLat = null;
            finderLng = null;
        }
        if (!isOwnerReady(now)) {
            ownerLat = null;
            ownerLng = null;
        }
    }

    public void setPlace(Building building, Integer finderWalkSeconds, Integer ownerWalkSeconds, LocalDateTime now) {
        this.status = HandoverStatus.PLACE_SET;
        this.building = building;
        this.finderWalkSeconds = finderWalkSeconds;
        this.ownerWalkSeconds = ownerWalkSeconds;
        this.placeSetAt = now;
        clearAllCoordinates();
    }

    /** 다시 정하기: 두 사람 상태와 추천 건물 초기화 */
    public void reset() {
        this.status = HandoverStatus.WAITING;
        this.finderReadyUntil = null;
        this.ownerReadyUntil = null;
        this.building = null;
        this.finderWalkSeconds = null;
        this.ownerWalkSeconds = null;
        this.placeSetAt = null;
        clearAllCoordinates();
    }

    public void complete(LocalDateTime now) {
        this.status = HandoverStatus.DONE;
        this.completedAt = now;
        clearAllCoordinates();
    }

    private void clearAllCoordinates() {
        finderLat = null;
        finderLng = null;
        ownerLat = null;
        ownerLng = null;
    }
}
