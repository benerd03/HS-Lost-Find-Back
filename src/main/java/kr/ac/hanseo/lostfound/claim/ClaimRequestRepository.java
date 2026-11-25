package kr.ac.hanseo.lostfound.claim;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRequestRepository extends JpaRepository<ClaimRequest, Long> {

    /** 내가 이 습득물에 낸 신청 (UNIQUE라 최대 1건) */
    @Query("select c from ClaimRequest c where c.foundItem.id = :foundItemId and c.student.studentId = :studentId")
    Optional<ClaimRequest> findMine(@Param("foundItemId") Long foundItemId, @Param("studentId") String studentId);

    /** 여러 습득물에 대한 내 신청 (매칭 후보의 claimEligibility 계산용) */
    @Query("select c from ClaimRequest c where c.student.studentId = :studentId and c.foundItem.id in :foundItemIds")
    List<ClaimRequest> findMineIn(@Param("studentId") String studentId,
            @Param("foundItemIds") Collection<Long> foundItemIds);

    /** 이 분실 신고에 연결된 신청 중 지정한 상태가 있는지 */
    @Query("select count(c) > 0 from ClaimRequest c where c.lostItem.id = :lostItemId and c.status in :statuses")
    boolean existsByLostItemAndStatusIn(@Param("lostItemId") Long lostItemId,
            @Param("statuses") Collection<ClaimStatus> statuses);

    /** 분실 신고를 지우기 전에 남은 신청의 연결만 끊는다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ClaimRequest c set c.lostItem = null where c.lostItem.id = :lostItemId")
    int unlinkLostItem(@Param("lostItemId") Long lostItemId);

    /** 이 습득물에 남은 신청 중 지정한 상태가 있는지 (취소·거절 후 STORED로 되돌릴지 판단) */
    @Query("select count(c) > 0 from ClaimRequest c where c.foundItem.id = :foundItemId and c.status = :status")
    boolean existsByFoundItemAndStatus(@Param("foundItemId") Long foundItemId, @Param("status") ClaimStatus status);

    /** 내 수령 신청 전체, 최신순 (습득물 함께 읽음) */
    @Query("""
            select c from ClaimRequest c join fetch c.foundItem
            where c.student.studentId = :studentId order by c.requestedAt desc, c.id desc""")
    List<ClaimRequest> findAllMine(@Param("studentId") String studentId);

    /** 습득물별 PENDING 신청 수: [foundItemId, count] */
    @Query("""
            select c.foundItem.id, count(c) from ClaimRequest c
            where c.foundItem.id in :foundItemIds and c.status = kr.ac.hanseo.lostfound.claim.ClaimStatus.PENDING
            group by c.foundItem.id""")
    List<Object[]> countPendingByFoundItem(@Param("foundItemIds") Collection<Long> foundItemIds);

    @Query("select count(c) from ClaimRequest c where c.student.studentId = :studentId and c.status in :statuses")
    long countMine(@Param("studentId") String studentId, @Param("statuses") Collection<ClaimStatus> statuses);

    /**
     * 신청의 습득물 ID만 읽는다. 엔티티를 미리 읽어 두면 습득물을 잠근 뒤 다시 읽어도
     * 캐시된 옛 상태가 나오므로, 잠그기 전에는 ID만 가져온다.
     */
    @Query("select c.foundItem.id from ClaimRequest c where c.id = :claimId")
    Optional<Long> findFoundItemIdOf(@Param("claimId") Long claimId);

    /** 습득물별 전체 신청 수(모든 상태): [foundItemId, count] (관리자 A2 목록) */
    @Query("""
            select c.foundItem.id, count(c) from ClaimRequest c
            where c.foundItem.id in :foundItemIds group by c.foundItem.id""")
    List<Object[]> countAllByFoundItem(@Param("foundItemIds") Collection<Long> foundItemIds);

    /**
     * A3 묶음 순서: 지정한 상태의 신청이 있는 습득물 ID를, 그 상태 신청 중 가장 오래된 신청 시각 순으로.
     * foundItemId가 있으면 그 습득물만. 삭제된 습득물은 뺀다.
     */
    @Query("""
            select c.foundItem.id from ClaimRequest c
            where c.status = :status and c.foundItem.deletedAt is null
              and (:foundItemId is null or c.foundItem.id = :foundItemId)
            group by c.foundItem.id
            order by min(c.requestedAt) asc, c.foundItem.id asc""")
    List<Long> findFoundItemIdsWithStatus(@Param("status") ClaimStatus status,
            @Param("foundItemId") Long foundItemId);

    /** 습득물들의 신청 전체 (신청자·연결 분실 신고 함께 읽음), 오래된 순 */
    @Query("""
            select c from ClaimRequest c join fetch c.student left join fetch c.lostItem l left join fetch l.building
            where c.foundItem.id in :foundItemIds order by c.requestedAt asc, c.id asc""")
    List<ClaimRequest> findAllForAdmin(@Param("foundItemIds") Collection<Long> foundItemIds);

    /** 주인 확정 시 함께 거절할, 이 습득물의 나머지 PENDING 신청 */
    @Query("""
            select c from ClaimRequest c join fetch c.student
            where c.foundItem.id = :foundItemId and c.id <> :exceptClaimId
              and c.status = kr.ac.hanseo.lostfound.claim.ClaimStatus.PENDING
            order by c.id asc""")
    List<ClaimRequest> findOtherPending(@Param("foundItemId") Long foundItemId,
            @Param("exceptClaimId") Long exceptClaimId);
}
