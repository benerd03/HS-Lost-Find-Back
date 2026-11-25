package kr.ac.hanseo.lostfound.founditem;

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

/**
 * 습득물. 물건은 수령완료 전까지 습득자(finder)가 보관한다.
 * 삭제는 소프트 삭제(deletedAt). 조회 쿼리에서 항상 deletedAt IS NULL 조건을 건다.
 * 상태를 보고 판단하는 변경은 FoundItemRepository.findByIdForUpdate로 행을 잠근 뒤 한다 (명세 "동시성").
 */
@Entity
@Table(name = "found_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "found_item_id")
    private Long id;

    /** 습득자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student finder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "found_at", nullable = false)
    private LocalDateTime foundAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "found_building_id", nullable = false)
    private Building building;

    @Column(name = "location_detail", length = 100)
    private String locationDetail;

    @Column(length = 1000)
    private String description;

    /** /uploads/uuid.jpg */
    @Column(name = "image_path", nullable = false)
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private FoundItemStatus status = FoundItemStatus.STORED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public FoundItem(Student finder, Category category, String itemName, LocalDateTime foundAt,
            Building building, String locationDetail, String description, String imagePath) {
        this.finder = finder;
        this.category = category;
        this.itemName = itemName;
        this.foundAt = foundAt;
        this.building = building;
        this.locationDetail = locationDetail;
        this.description = description;
        this.imagePath = imagePath;
    }

    public void update(Category category, String itemName, LocalDateTime foundAt,
            Building building, String locationDetail, String description) {
        this.category = category;
        this.itemName = itemName;
        this.foundAt = foundAt;
        this.building = building;
        this.locationDetail = locationDetail;
        this.description = description;
    }

    public void changeImage(String imagePath) {
        this.imagePath = imagePath;
    }

    public void changeStatus(FoundItemStatus status) {
        this.status = status;
    }

    public void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isFoundBy(String studentId) {
        return finder.getStudentId().equals(studentId);
    }
}
