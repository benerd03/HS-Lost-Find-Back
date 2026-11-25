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
}
