package kr.ac.hanseo.lostfound.catalog;

import java.util.List;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.pathgraph.PlaceRecommendationService;
import lombok.RequiredArgsConstructor;

/** 카테고리·건물 목록 (로그인 없이 조회 가능) */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogController {

    private final CategoryRepository categoryRepository;
    private final BuildingRepository buildingRepository;
    private final PlaceRecommendationService placeRecommendationService;

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

    /** demoSelectable: 장소 추천 후보가 될 수 있는 건물 (시연 모드의 현재 위치로 고를 수 있음) */
    @GetMapping("/buildings")
    public ApiResponse<List<BuildingResponse>> buildings() {
        Set<Long> selectable = placeRecommendationService.selectableBuildingIds();
        return ApiResponse.ok(buildingRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(b -> new BuildingResponse(b.getId(), b.getName(), selectable.contains(b.getId())))
                .toList());
    }
}
