package kr.ac.hanseo.lostfound.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.pathgraph.PathGraphService;
import kr.ac.hanseo.lostfound.pathgraph.PathGraphService.GraphStatus;
import kr.ac.hanseo.lostfound.pathgraph.PathGraphService.Route;
import lombok.RequiredArgsConstructor;

/** 보행 그래프 관리 (운영용, 화면 없음). 데이터 담당이 CSV를 고친 뒤 확인·반영할 때 쓴다 */
@RestController
@RequestMapping("/api/admin/path-graph")
@RequiredArgsConstructor
public class AdminPathGraphController {

    private final PathGraphService pathGraphService;

    @GetMapping
    public ApiResponse<GraphStatus> status() {
        return ApiResponse.ok(pathGraphService.status());
    }

    /** data/ 폴더의 CSV를 다시 읽어 그래프를 교체. CSV 오류면 400이고 기존 그래프는 그대로 */
    @PostMapping("/rebuild")
    public ApiResponse<GraphStatus> rebuild() {
        return ApiResponse.ok(pathGraphService.rebuild());
    }

    /** 실측 보정용: 노드 → 건물의 계산된 시간과 지나는 노드 */
    @GetMapping("/route")
    public ApiResponse<Route> route(@RequestParam String fromNode, @RequestParam String toBuilding) {
        return ApiResponse.ok(pathGraphService.route(fromNode, toBuilding));
    }
}
