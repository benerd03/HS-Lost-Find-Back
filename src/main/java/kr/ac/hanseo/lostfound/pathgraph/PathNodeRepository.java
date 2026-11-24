package kr.ac.hanseo.lostfound.pathgraph;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PathNodeRepository extends JpaRepository<PathNode, Long> {

    /** 출입구 노드가 있는 건물 ID (보행 그래프 모드의 추천 후보) */
    @Query("select distinct n.building.id from PathNode n where n.type = kr.ac.hanseo.lostfound.pathgraph.NodeType.ENTRANCE")
    Set<Long> findBuildingIdsWithEntrance();
}
