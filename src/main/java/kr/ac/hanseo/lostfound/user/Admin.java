package kr.ac.hanseo.lostfound.user;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin {

    @Id
    @Column(name = "admin_id", length = 20)
    private String adminId;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, length = 50)
    private String department;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Admin(String adminId, String passwordHash, String name, String department) {
        this.adminId = adminId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.department = department;
    }
}
