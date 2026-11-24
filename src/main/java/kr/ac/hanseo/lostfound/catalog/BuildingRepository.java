package kr.ac.hanseo.lostfound.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    List<Building> findAllByOrderBySortOrderAscIdAsc();

    Optional<Building> findByCode(String code);
}
