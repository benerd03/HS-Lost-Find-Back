package kr.ac.hanseo.lostfound.handover;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.common.auth.LoginUser;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.auth.StudentOnly;
import lombok.RequiredArgsConstructor;

/** 전달 진행 패널 S10 (FR-07). 두 당사자(학생)만 */
@RestController
@StudentOnly
@RequiredArgsConstructor
public class HandoverController {

    private final HandoverService handoverService;

    @GetMapping("/api/me/handovers")
    public ApiResponse<List<HandoverView>> mine(@LoginUser SessionUser user) {
        return ApiResponse.ok(handoverService.mine(user));
    }

    @GetMapping("/api/handovers/{id}")
    public ApiResponse<HandoverView> get(@PathVariable Long id, @LoginUser SessionUser user) {
        return ApiResponse.ok(handoverService.get(id, user));
    }

    @PostMapping("/api/handovers/{id}/ready")
    public ApiResponse<HandoverView> ready(@PathVariable Long id,
            @Valid @RequestBody(required = false) ReadyRequest request, @LoginUser SessionUser user) {
        return ApiResponse.ok(handoverService.ready(id, request, user));
    }

    @PostMapping("/api/handovers/{id}/reset")
    public ApiResponse<HandoverView> reset(@PathVariable Long id, @LoginUser SessionUser user) {
        return ApiResponse.ok(handoverService.reset(id, user));
    }

    @PostMapping("/api/handovers/{id}/receive")
    public ApiResponse<HandoverView> receive(@PathVariable Long id, @LoginUser SessionUser user) {
        return ApiResponse.ok(handoverService.receive(id, user));
    }
}
