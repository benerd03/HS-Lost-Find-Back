package kr.ac.hanseo.lostfound.notification;

import java.time.LocalDateTime;

/** 알림 목록 항목. message는 서버가 만든 문구를 그대로 띄운다. 이동할 곳이 없으면 link는 null */
public record NotificationView(Long notificationId, NotificationType type, String message, boolean isRead,
        LocalDateTime createdAt, Link link) {

    /** kind: HANDOVER(전달 패널), CLAIM(내 수령 신청), FOUND_ITEM(내 습득물) */
    public record Link(LinkKind kind, Long id) {
    }

    public static NotificationView from(Notification n) {
        Link link = n.getLinkKind() == null ? null : new Link(n.getLinkKind(), n.getLinkId());
        return new NotificationView(n.getId(), n.getType(), n.getMessage(), n.isRead(), n.getCreatedAt(), link);
    }
}
