package kr.ac.hanseo.lostfound.claim;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
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
import kr.ac.hanseo.lostfound.founditem.FoundItem;
import kr.ac.hanseo.lostfound.lostitem.LostItem;
import kr.ac.hanseo.lostfound.user.Admin;
import kr.ac.hanseo.lostfound.user.Student;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수령 신청. UNIQUE(student_id, found_item_id)로 재신청이 막힌다 (취소·거절 포함). */
@Entity
@Table(name = "claim_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "found_item_id", nullable = false)
    private FoundItem foundItem;

    /** 신청자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** 연결한 분실 신고 (선택) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lost_item_id")
    private LostItem lostItem;

    @Column(name = "ownership_evidence", nullable = false, length = 1000)
    private String ownershipEvidence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "approval_status", nullable = false, length = 10)
    private ClaimStatus status = ClaimStatus.PENDING;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    /** 관리자 확정·거절 시각 (다른 신청 확정으로 자동 거절된 경우 포함) */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_admin_id")
    private Admin processedAdmin;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public ClaimRequest(FoundItem foundItem, Student student, LostItem lostItem, String ownershipEvidence) {
        this.foundItem = foundItem;
        this.student = student;
        this.lostItem = lostItem;
        this.ownershipEvidence = ownershipEvidence;
    }

    public void approve(Admin admin, LocalDateTime now) {
        this.status = ClaimStatus.APPROVED;
        this.processedAdmin = admin;
        this.processedAt = now;
    }

    public void reject(Admin admin, String reason, LocalDateTime now) {
        this.status = ClaimStatus.REJECTED;
        this.processedAdmin = admin;
        this.rejectReason = reason;
        this.processedAt = now;
    }

    public void cancel(LocalDateTime now) {
        this.status = ClaimStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void complete(LocalDateTime now) {
        this.status = ClaimStatus.COMPLETED;
        this.completedAt = now;
    }

    public void unlinkLostItem() {
        this.lostItem = null;
    }

    public boolean isRequestedBy(String studentId) {
        return student.getStudentId().equals(studentId);
    }
}
