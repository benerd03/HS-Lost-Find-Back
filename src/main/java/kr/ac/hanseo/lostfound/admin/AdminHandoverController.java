package kr.ac.hanseo.lostfound.admin;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.ac.hanseo.lostfound.admin.AdminHandoverService.HandoverList;
import kr.ac.hanseo.lostfound.admin.AdminHandoverService.HandoverRow;
import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;

/** 관리자 A4 전달 진행 현황 (보기 전용, 수령완료 처리는 관리자가 하지 않는다) */
@RestController
@RequestMapping("/api/admin/handovers")
@RequiredArgsConstructor
public class AdminHandoverController {

    private final AdminHandoverService adminHandoverService;

    @GetMapping
    public ApiResponse<HandoverList> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(adminHandoverService.list(status, from, to));
    }

    @GetMapping("/{id}")
    public ApiResponse<HandoverRow> detail(@PathVariable Long id) {
        return ApiResponse.ok(adminHandoverService.detail(id));
    }
}
