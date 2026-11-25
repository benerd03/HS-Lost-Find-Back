package kr.ac.hanseo.lostfound.admin;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.founditem.FoundItemRepository;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import lombok.RequiredArgsConstructor;

/** A1 대시보드. /api/admin/** 는 AuthInterceptor가 관리자만 통과시킨다 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSummaryController {

    private final FoundItemRepository foundItemRepository;

    public record SummaryResponse(Map<FoundItemStatus, Long> counts, long total) {
    }

    /** 건수가 0인 상태도 0으로 보낸다. total은 네 상태의 합(삭제된 습득물 제외) */
    @GetMapping("/summary")
    public ApiResponse<SummaryResponse> summary() {
        Map<FoundItemStatus, Long> counts = new EnumMap<>(FoundItemStatus.class);
        for (FoundItemStatus status : FoundItemStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : foundItemRepository.countByStatus()) {
            counts.put((FoundItemStatus) row[0], (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return ApiResponse.ok(new SummaryResponse(new LinkedHashMap<>(counts), total));
    }
}
