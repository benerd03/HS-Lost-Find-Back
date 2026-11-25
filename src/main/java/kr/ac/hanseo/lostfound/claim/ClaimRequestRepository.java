package kr.ac.hanseo.lostfound.claim;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRequestRepository extends JpaRepository<ClaimRequest, Long> {

    /** 내가 이 습득물에 낸 신청 (UNIQUE라 최대 1건) */
    @Query("select c from ClaimRequest c where c.foundItem.id = :foundItemId and c.student.studentId = :studentId")
    Optional<ClaimRequest> findMine(@Param("foundItemId") Long foundItemId, @Param("studentId") String studentId);
}
