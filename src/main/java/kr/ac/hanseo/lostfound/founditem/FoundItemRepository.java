package kr.ac.hanseo.lostfound.founditem;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface FoundItemRepository extends JpaRepository<FoundItem, Long>, JpaSpecificationExecutor<FoundItem> {

    /** 삭제되지 않은 습득물 (카테고리·건물 함께 읽음) */
    @Query("select f from FoundItem f join fetch f.category join fetch f.building where f.id = :id and f.deletedAt is null")
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

    /** 내 습득 신고 (삭제 제외), 최신순 */
    @Query("""
            select f from FoundItem f join fetch f.category join fetch f.building
            where f.finder.studentId = :studentId and f.deletedAt is null
            order by f.createdAt desc, f.id desc""")
    List<FoundItem> findMine(@Param("studentId") String studentId);

    /** 내 진행 중 습득 신고 수 (삭제·수령완료 제외) */
    @Query("""
            select count(f) from FoundItem f where f.finder.studentId = :studentId and f.deletedAt is null
              and f.status <> kr.ac.hanseo.lostfound.founditem.FoundItemStatus.CLAIMED""")
    long countOngoingMine(@Param("studentId") String studentId);

    /** 여러 습득물을 카테고리·건물과 함께 (관리자 A3 묶음) */
    @Query("select f from FoundItem f join fetch f.category join fetch f.building where f.id in :ids")
    List<FoundItem> findAllWithDetailsByIdIn(@Param("ids") java.util.Collection<Long> ids);
}
