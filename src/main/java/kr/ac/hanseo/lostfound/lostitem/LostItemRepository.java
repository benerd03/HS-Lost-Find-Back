package kr.ac.hanseo.lostfound.lostitem;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LostItemRepository extends JpaRepository<LostItem, Long> {

    @Query("select l from LostItem l join fetch l.category join fetch l.building where l.id = :id")
    Optional<LostItem> findWithDetailsById(@Param("id") Long id);

    /** 수정·삭제·찾았음은 행을 잠근 뒤 상태를 확인한다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LostItem l where l.id = :id")
    Optional<LostItem> findByIdForUpdate(@Param("id") Long id);

    /** 내 분실 신고 (status가 null이면 전체), 최신순 */
    @Query("""
            select l from LostItem l join fetch l.category join fetch l.building
            where l.student.studentId = :studentId and (:status is null or l.status = :status)
            order by l.createdAt desc, l.id desc""")
    List<LostItem> findMine(@Param("studentId") String studentId, @Param("status") LostItemStatus status);
}
