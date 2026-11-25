package kr.ac.hanseo.lostfound.pathgraph;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkTimeRepository extends JpaRepository<WalkTime, WalkTime.Key> {
}
