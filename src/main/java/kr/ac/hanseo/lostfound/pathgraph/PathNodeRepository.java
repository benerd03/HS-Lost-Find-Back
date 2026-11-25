package kr.ac.hanseo.lostfound.pathgraph;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PathNodeRepository extends JpaRepository<PathNode, Long> {

    /** 출입구 노드가 있는 건물 ID */
    @Query("select distinct n.building.id from PathNode n where n.type = kr.ac.hanseo.lostfound.pathgraph.NodeType.ENTRANCE")
    Set<Long> findBuildingIdsWithEntrance();

    @Query("select n from PathNode n left join fetch n.building order by n.id")
    List<PathNode> findAllWithBuilding();

    Optional<PathNode> findByCode(String code);

    /** 시연 모드: 건물의 출입구 중 node_id가 가장 작은 것 */
    @Query("""
            select n from PathNode n where n.building.id = :buildingId
              and n.type = kr.ac.hanseo.lostfound.pathgraph.NodeType.ENTRANCE order by n.id""")
    List<PathNode> findEntrancesOf(@Param("buildingId") Long buildingId);
}
