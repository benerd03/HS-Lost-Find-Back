package kr.ac.hanseo.lostfound.mypage;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.common.auth.LoginUser;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.auth.StudentOnly;
import kr.ac.hanseo.lostfound.mypage.MyPageService.MyClaim;
import kr.ac.hanseo.lostfound.mypage.MyPageService.MyFoundItem;
import kr.ac.hanseo.lostfound.mypage.MyPageService.Summary;
import lombok.RequiredArgsConstructor;

/** 홈 "내 진행 현황"과 마이페이지 탭. 내 분실 신고(/api/me/lost-items)는 LostItemController에 있다 */
@RestController
@StudentOnly
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/summary")
    public ApiResponse<Summary> summary(@LoginUser SessionUser user) {
        return ApiResponse.ok(myPageService.summary(user.loginId()));
    }

    @GetMapping("/found-items")
    public ApiResponse<List<MyFoundItem>> foundItems(@LoginUser SessionUser user) {
        return ApiResponse.ok(myPageService.foundItems(user.loginId()));
    }

    @GetMapping("/claims")
    public ApiResponse<List<MyClaim>> claims(@LoginUser SessionUser user) {
        return ApiResponse.ok(myPageService.claims(user.loginId()));
    }
}
