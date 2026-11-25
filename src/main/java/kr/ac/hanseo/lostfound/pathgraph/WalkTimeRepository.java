package kr.ac.hanseo.lostfound.pathgraph;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalkTimeRepository extends JpaRepository<WalkTime, WalkTime.Key> {

    /** 장소 추천: 두 사람의 노드에서 각 건물까지의 시간 (PK 앞부분 from_node_id 인덱스로 범위 조회) */
    @Query("select w from WalkTime w where w.fromNodeId in :nodeIds")
    List<WalkTime> findFrom(@Param("nodeIds") Collection<Long> nodeIds);

    @Query("select distinct w.toBuildingId from WalkTime w")
    Set<Long> findReachableBuildingIds();

    @Query("select max(w.computedAt) from WalkTime w")
    LocalDateTime findComputedAt();
}
