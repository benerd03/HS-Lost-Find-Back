package kr.ac.hanseo.lostfound.admin;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import kr.ac.hanseo.lostfound.admin.AdminResponses.ApproveResult;
import kr.ac.hanseo.lostfound.admin.AdminResponses.ClaimBundle;
import kr.ac.hanseo.lostfound.admin.AdminResponses.FoundItemDetail;
import kr.ac.hanseo.lostfound.admin.AdminResponses.FoundItemRow;
import kr.ac.hanseo.lostfound.admin.AdminResponses.RejectResult;
import kr.ac.hanseo.lostfound.claim.ClaimStatus;
import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.common.api.PageParams;
import kr.ac.hanseo.lostfound.common.api.PageResponse;
import kr.ac.hanseo.lostfound.common.auth.LoginUser;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.founditem.FoundItemStatus;
import lombok.RequiredArgsConstructor;

/** 관리자 A2(습득물), A3(신청 처리). /api/admin/** 는 AuthInterceptor가 관리자만 통과시킨다 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Sort LATEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AdminFoundItemService adminFoundItemService;
    private final AdminClaimService adminClaimService;

    public record RejectRequest(@Size(max = 255) String reason) {
    }

    @GetMapping("/found-items")
    public ApiResponse<PageResponse<FoundItemRow>> foundItems(
            @RequestParam(required = false) FoundItemStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var result = adminFoundItemService.foundItems(status, PageParams.of(page, size, LATEST));
        return ApiResponse.ok(PageResponse.of(result, r -> r));
    }

    @GetMapping("/found-items/{id}")
    public ApiResponse<FoundItemDetail> foundItem(@PathVariable Long id) {
        return ApiResponse.ok(adminFoundItemService.foundItem(id));
    }

    @GetMapping("/claims")
    public ApiResponse<List<ClaimBundle>> claims(@RequestParam(required = false) ClaimStatus claimStatus,
            @RequestParam(required = false) Long foundItemId) {
        return ApiResponse.ok(adminFoundItemService.bundles(claimStatus, foundItemId));
    }

    @GetMapping("/found-items/{id}/claims")
    public ApiResponse<ClaimBundle> claimsOf(@PathVariable Long id) {
        return ApiResponse.ok(adminFoundItemService.bundleOf(id));
    }

    @PostMapping("/claims/{id}/approve")
    public ApiResponse<ApproveResult> approve(@PathVariable Long id, @LoginUser SessionUser admin) {
        return ApiResponse.ok(adminClaimService.approve(id, admin));
    }

    /** 사유는 선택. 본문 없이 보내도 된다 */
    @PostMapping("/claims/{id}/reject")
    public ApiResponse<RejectResult> reject(@PathVariable Long id,
            @Valid @RequestBody(required = false) RejectRequest request, @LoginUser SessionUser admin) {
        return ApiResponse.ok(adminClaimService.reject(id, request == null ? null : request.reason(), admin));
    }
}
