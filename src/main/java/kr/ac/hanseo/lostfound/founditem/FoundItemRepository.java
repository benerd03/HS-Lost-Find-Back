package kr.ac.hanseo.lostfound.founditem;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface FoundItemRepository extends JpaRepository<FoundItem, Long> {

    /** 삭제되지 않은 습득물 */
    @Query("select f from FoundItem f where f.id = :id and f.deletedAt is null")
    Optional<FoundItem> findActiveById(@Param("id") Long id);

    /**
     * SELECT ... FOR UPDATE. 신청 생성·취소, 주인 확정·거절, 습득물 수정·삭제는
     * 반드시 이걸로 먼저 잠그고, 잠근 뒤 다시 읽은 상태로 판단한다 (명세 "동시성").
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FoundItem f where f.id = :id and f.deletedAt is null")
    Optional<FoundItem> findByIdForUpdate(@Param("id") Long id);

    /** A1 상태별 건수: [status, count] */
    @Query("select f.status, count(f) from FoundItem f where f.deletedAt is null group by f.status")
    List<Object[]> countByStatus();
}
