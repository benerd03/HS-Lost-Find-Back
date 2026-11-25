package kr.ac.hanseo.lostfound.claim;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.ac.hanseo.lostfound.claim.ClaimService.ClaimResult;
import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.common.auth.LoginUser;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.auth.StudentOnly;
import lombok.RequiredArgsConstructor;

/** 수령 신청·취소 (학생만) */
@RestController
@StudentOnly
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @PostMapping("/api/found-items/{id}/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ClaimResult> create(@PathVariable Long id, @Valid @RequestBody ClaimCreateRequest request,
            @LoginUser SessionUser user) {
        return ApiResponse.ok(claimService.create(id, request, user));
    }

    @PatchMapping("/api/claims/{id}/cancel")
    public ApiResponse<ClaimResult> cancel(@PathVariable Long id, @LoginUser SessionUser user) {
        return ApiResponse.ok(claimService.cancel(id, user));
    }
}
