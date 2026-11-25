package kr.ac.hanseo.lostfound.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
}
