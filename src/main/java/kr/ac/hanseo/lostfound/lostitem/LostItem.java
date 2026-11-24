package kr.ac.hanseo.lostfound.lostitem;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kr.ac.hanseo.lostfound.catalog.Building;
import kr.ac.hanseo.lostfound.catalog.Category;
import kr.ac.hanseo.lostfound.user.Student;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lost_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LostItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lost_item_id")
    private Long id;

    /** 신고자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "lost_at", nullable = false)
    private LocalDateTime lostAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lost_building_id", nullable = false)
    private Building building;

    @Column(name = "location_detail", length = 100)
    private String locationDetail;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private LostItemStatus status = LostItemStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LostItem(Student student, Category category, String itemName, LocalDateTime lostAt,
            Building building, String locationDetail, String description) {
        this.student = student;
        this.category = category;
        this.itemName = itemName;
        this.lostAt = lostAt;
        this.building = building;
        this.locationDetail = locationDetail;
        this.description = description;
    }

    public void update(Category category, String itemName, LocalDateTime lostAt,
            Building building, String locationDetail, String description) {
        this.category = category;
        this.itemName = itemName;
        this.lostAt = lostAt;
        this.building = building;
        this.locationDetail = locationDetail;
        this.description = description;
    }

    public void close() {
        this.status = LostItemStatus.CLOSED;
    }

    public boolean isOwnedBy(String studentId) {
        return student.getStudentId().equals(studentId);
    }
}
