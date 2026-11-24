package kr.ac.hanseo.lostfound.catalog;

import java.util.List;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.pathgraph.PathNodeRepository;
import lombok.RequiredArgsConstructor;

/** 카테고리·건물 목록 (로그인 없이 조회 가능) */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogController {

    private final CategoryRepository categoryRepository;
    private final BuildingRepository buildingRepository;
    private final PathNodeRepository pathNodeRepository;

    public record CategoryResponse(Long categoryId, String name) {
    }

    /** 좌표는 보내지 않는다 */
    public record BuildingResponse(Long buildingId, String name, boolean demoSelectable) {
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> categories() {
        return ApiResponse.ok(categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName()))
                .toList());
    }

    /**
     * demoSelectable: 보행 그래프 모드면 출입구 노드가 있는 건물,
     * 그래프 데이터가 아직 없으면(직선거리 대체 모드) 좌표가 있는 건물
     */
    @GetMapping("/buildings")
    public ApiResponse<List<BuildingResponse>> buildings() {
        Set<Long> withEntrance = pathNodeRepository.findBuildingIdsWithEntrance();
        boolean graphMode = !withEntrance.isEmpty();
        return ApiResponse.ok(buildingRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(b -> new BuildingResponse(b.getId(), b.getName(),
                        graphMode ? withEntrance.contains(b.getId()) : b.hasCoordinate()))
                .toList());
    }
}
