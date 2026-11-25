package kr.ac.hanseo.lostfound.notification;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 최신순 (인덱스: student_id, is_read, created_at) */
    @Query("select n from Notification n where n.student.studentId = :studentId order by n.createdAt desc, n.id desc")
    List<Notification> findLatest(@Param("studentId") String studentId, Pageable pageable);

    /** 종 아이콘의 빨간 점 (30초마다 호출) */
    @Query("select count(n) from Notification n where n.student.studentId = :studentId and n.read = false")
    long countUnread(@Param("studentId") String studentId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.student.studentId = :studentId and n.read = false")
    int markAllRead(@Param("studentId") String studentId);
}
