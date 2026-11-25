package kr.ac.hanseo.lostfound.pathgraph;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PathEdgeRepository extends JpaRepository<PathEdge, Long> {

    @Query("select e from PathEdge e join fetch e.fromNode join fetch e.toNode order by e.id")
    List<PathEdge> findAllWithNodes();
}
