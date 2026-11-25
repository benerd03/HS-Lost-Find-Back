package kr.ac.hanseo.lostfound.lostitem;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.common.auth.LoginUser;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.auth.StudentOnly;
import kr.ac.hanseo.lostfound.lostitem.LostItemResponses.Created;
import kr.ac.hanseo.lostfound.lostitem.LostItemResponses.Detail;
import kr.ac.hanseo.lostfound.lostitem.LostItemResponses.Matches;
import lombok.RequiredArgsConstructor;

/** 분실 신고와 자동 매칭 (FR-02, FR-04). 모두 학생 본인만 */
@RestController
@StudentOnly
@RequiredArgsConstructor
public class LostItemController {

    private final LostItemService lostItemService;

    @PostMapping("/api/lost-items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Created> create(@Valid @RequestBody LostItemRequest request, @LoginUser SessionUser user) {
        return ApiResponse.ok(lostItemService.create(request, user));
    }

    @GetMapping("/api/lost-items/{id}")
    public ApiResponse<Detail> detail(@PathVariable Long id, @LoginUser SessionUser user) {
        return ApiResponse.ok(lostItemService.detail(id, user));
    }

    @PutMapping("/api/lost-items/{id}")
    public ApiResponse<Detail> update(@PathVariable Long id, @Valid @RequestBody LostItemRequest request,
            @LoginUser SessionUser user) {
        return ApiResponse.ok(lostItemService.update(id, request, user));
    }

    @DeleteMapping("/api/lost-items/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @LoginUser SessionUser user) {
        lostItemService.delete(id, user);
        return ApiResponse.ok();
    }

    /** 후보 다시 조회: 신고 뒤 새로 올라온 습득물도 포함된다 */
    @GetMapping("/api/lost-items/{id}/matches")
    public ApiResponse<Matches> matches(@PathVariable Long id, @LoginUser SessionUser user) {
        return ApiResponse.ok(new Matches(lostItemService.matches(id, user)));
    }

    /** "찾았음" */
    @PatchMapping("/api/lost-items/{id}/close")
    public ApiResponse<Detail> close(@PathVariable Long id, @LoginUser SessionUser user) {
        return ApiResponse.ok(lostItemService.close(id, user));
    }

    /** 내 분실 신고 (S9 탭, S8의 "연결할 분실 신고" 선택). status 생략 시 전체 */
    @GetMapping("/api/me/lost-items")
    public ApiResponse<List<Detail>> mine(@RequestParam(required = false) LostItemStatus status,
            @LoginUser SessionUser user) {
        return ApiResponse.ok(lostItemService.mine(user, status));
    }
}
