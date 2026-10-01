package com.arch3d.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "username", nullable = false, length = 64, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email", length = 128, unique = true)
    private String email;

    @Column(name = "avatar", length = 255)
    private String avatar;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    @Column(name = "nickname", length = 64)
    private String nickname;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Spring Security 的 UserDetails 要从 getPassword() 拿密码
    public String getPassword() {
        return passwordHash;
    }

    public boolean getEnabled() {
        return true;
    }

    // Builder 生成的没有 password 方法，补一个对齐 passwordHash
    public static class UserBuilder {
        public UserBuilder password(String password) {
            this.passwordHash = password;
            return this;
        }
    }
}
