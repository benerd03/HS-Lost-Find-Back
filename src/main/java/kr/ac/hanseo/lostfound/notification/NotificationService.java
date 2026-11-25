package kr.ac.hanseo.lostfound.notification;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import kr.ac.hanseo.lostfound.common.error.BusinessException;
import kr.ac.hanseo.lostfound.common.error.ErrorCode;
import kr.ac.hanseo.lostfound.user.Student;
import lombok.RequiredArgsConstructor;

/**
 * 알림 저장. 상태를 바꾸는 서비스가 같은 트랜잭션 안에서 부른다 (명세: 알림은 상태 변경과 같은 트랜잭션).
 * 문구는 서버가 만들고 프론트는 그대로 띄운다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(Student to, NotificationType type, String message, LinkKind linkKind, Long linkId) {
        notificationRepository.save(new Notification(to, type, message, linkKind, linkId));
    }

    /** 목록에 보여줄 최근 알림 수 (명세: 최근 30건) */
    public static final int LIST_SIZE = 30;

    @Transactional(readOnly = true)
    public List<NotificationView> latest(String studentId) {
        return notificationRepository.findLatest(studentId, PageRequest.of(0, LIST_SIZE)).stream()
                .map(NotificationView::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String studentId) {
        return notificationRepository.countUnread(studentId);
    }

    /** 읽음 처리 (본인 알림만). 이미 읽은 알림도 200 */
    @Transactional
    public NotificationView markRead(Long id, String studentId) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!n.getStudent().getStudentId().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        n.markRead();
        return NotificationView.from(n);
    }

    /** 모두 읽음. 바뀐 개수를 돌려준다 */
    @Transactional
    public int markAllRead(String studentId) {
        return notificationRepository.markAllRead(studentId);
    }
}
