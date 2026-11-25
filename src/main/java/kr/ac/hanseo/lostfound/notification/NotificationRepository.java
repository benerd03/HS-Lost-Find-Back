package kr.ac.hanseo.lostfound.notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n where n.student.studentId = :studentId order by n.createdAt desc, n.id desc")
    List<Notification> findLatest(@Param("studentId") String studentId,
            org.springframework.data.domain.Pageable pageable);
}
