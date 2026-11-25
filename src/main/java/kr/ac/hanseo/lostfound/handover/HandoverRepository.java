package kr.ac.hanseo.lostfound.handover;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface HandoverRepository extends JpaRepository<Handover, Long> {

    /** ready·reset·receive는 이걸로 잠근 뒤 처리 (두 사람이 거의 동시에 눌러도 건물 계산 1번) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Handover h where h.id = :id")
    Optional<Handover> findByIdForUpdate(@Param("id") Long id);

    /** 신청 ID → 전달 (내 수령 신청 목록의 handoverId) */
    @Query("select h from Handover h where h.claim.id in :claimIds")
    List<Handover> findByClaimIds(@Param("claimIds") Collection<Long> claimIds);

    /** 습득물 ID → 전달 (내 습득 신고 목록의 handoverId) */
    @Query("select h from Handover h join fetch h.claim c where c.foundItem.id in :foundItemIds")
    List<Handover> findByFoundItemIds(@Param("foundItemIds") Collection<Long> foundItemIds);

    /** 내가 습득자 또는 분실자인 진행 중(DONE 제외) 전달, 최근 생성순 */
    @Query("""
            select h from Handover h join h.claim c join c.foundItem f
            where h.status <> kr.ac.hanseo.lostfound.handover.HandoverStatus.DONE
              and (c.student.studentId = :studentId or f.finder.studentId = :studentId)
            order by h.createdAt desc, h.id desc""")
    List<Handover> findOngoingOf(@Param("studentId") String studentId, Pageable pageable);

    /** 조회 화면에 필요한 것을 한 번에 (신청, 신청자, 습득물, 습득자, 카테고리, 건물) */
    @Query("""
            select h from Handover h join fetch h.claim c join fetch c.student join fetch c.foundItem f
            join fetch f.finder join fetch f.category join fetch f.building left join fetch h.building
            where h.id = :id""")
    Optional<Handover> findWithDetailsById(@Param("id") Long id);

    /**
     * 30분이 지나 이동 가능이 풀린 사람의 임시 좌표를 지운다 (명세: 다음 조회 때 삭제).
     * 행 잠금 없이 조건부 UPDATE 한 번으로 처리해 5초 폴링이 서로 막히지 않게 한다.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            update Handover h set h.finderLat = null, h.finderLng = null
            where h.id = :id and h.finderLat is not null and (h.finderReadyUntil is null or h.finderReadyUntil <= :now)""")
    int clearExpiredFinderCoordinates(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            update Handover h set h.ownerLat = null, h.ownerLng = null
            where h.id = :id and h.ownerLat is not null and (h.ownerReadyUntil is null or h.ownerReadyUntil <= :now)""")
    int clearExpiredOwnerCoordinates(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    /** 관리자 A4 목록 (날짜는 주인 확정 시각 = 생성 시각 기준) */
    @Query("""
            select h from Handover h join fetch h.claim c join fetch c.student join fetch c.foundItem f
            join fetch f.finder left join fetch h.building
            where h.status in :statuses
              and (:from is null or h.createdAt >= :from) and (:to is null or h.createdAt < :to)
            order by h.createdAt desc, h.id desc""")
    List<Handover> findForAdmin(@Param("statuses") Collection<HandoverStatus> statuses,
            @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);

    /** 관리자 A4 집계: [status, count] (날짜 필터만 적용) */
    @Query("""
            select h.status, count(h) from Handover h
            where (:from is null or h.createdAt >= :from) and (:to is null or h.createdAt < :to)
            group by h.status""")
    List<Object[]> countByStatus(@Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);
}
