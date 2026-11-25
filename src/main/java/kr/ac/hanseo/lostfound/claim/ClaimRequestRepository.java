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
}
