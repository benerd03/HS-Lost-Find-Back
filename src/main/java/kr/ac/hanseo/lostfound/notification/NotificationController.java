package kr.ac.hanseo.lostfound.notification;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.ac.hanseo.lostfound.common.api.ApiResponse;
import kr.ac.hanseo.lostfound.common.auth.LoginUser;
import kr.ac.hanseo.lostfound.common.auth.SessionUser;
import kr.ac.hanseo.lostfound.common.auth.StudentOnly;
import lombok.RequiredArgsConstructor;

/** 공통 상단 종 아이콘 (알림은 학생에게만 간다) */
@RestController
@StudentOnly
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    public record UnreadCount(long unreadCount) {
    }

    public record ReadAllResult(int updatedCount) {
    }

    /** 최신순 최근 30건 */
    @GetMapping
    public ApiResponse<List<NotificationView>> list(@LoginUser SessionUser user) {
        return ApiResponse.ok(notificationService.latest(user.loginId()));
    }

    /** 빨간 점. 프론트가 30초마다 호출 */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount(@LoginUser SessionUser user) {
        return ApiResponse.ok(new UnreadCount(notificationService.unreadCount(user.loginId())));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationView> read(@PathVariable Long id, @LoginUser SessionUser user) {
        return ApiResponse.ok(notificationService.markRead(id, user.loginId()));
    }

    @PatchMapping("/read-all")
    public ApiResponse<ReadAllResult> readAll(@LoginUser SessionUser user) {
        return ApiResponse.ok(new ReadAllResult(notificationService.markAllRead(user.loginId())));
    }
}
