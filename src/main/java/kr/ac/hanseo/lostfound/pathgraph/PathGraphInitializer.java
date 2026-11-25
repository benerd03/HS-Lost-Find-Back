package kr.ac.hanseo.lostfound.pathgraph;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import kr.ac.hanseo.lostfound.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 서버 시작 시 (건물 동기화 다음 순서):
 * - PATH_NODE가 비어 있고 data/에 CSV가 있으면 읽어 넣고 WALK_TIME 계산
 * - 이미 있으면 WALK_TIME만 다시 계산 (계산식이나 상수를 바꿨을 때 반영되도록)
 * - 둘 다 없으면 아무것도 하지 않는다 → 장소 추천은 직선거리
 * CSV 오류가 있어도 서버는 뜨고, 로그에 이유를 남긴다.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class PathGraphInitializer implements ApplicationRunner {

    private final PathGraphService pathGraphService;
    private final PathNodeRepository nodeRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (nodeRepository.count() == 0) {
                if (pathGraphService.csvExists()) {
                    pathGraphService.rebuild();
                } else {
                    log.info("[path-graph] 보행 그래프 CSV가 없어 장소 추천은 직선거리로 동작합니다");
                }
            } else {
                pathGraphService.recompute();
            }
            var status = pathGraphService.status();
            if (!status.unreachableNodes().isEmpty()) {
                log.warn("[path-graph] 일부 건물에 닿지 못하는 노드: {}", status.unreachableNodes());
            }
        } catch (BusinessException e) {
            log.error("[path-graph] 보행 그래프를 읽지 못했습니다: {}", e.getMessage());
        }
    }
}
