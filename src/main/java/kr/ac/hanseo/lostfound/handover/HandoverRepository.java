package kr.ac.hanseo.lostfound.handover;

import java.util.Optional;

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
}
