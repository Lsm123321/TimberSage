package com.arch3d.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // 密码哈希只用于服务端认证，绝不能出现在任何 JSON 响应里。
    // 之前 AdminController 直接返回 User 实体，Jackson 会把 passwordHash
    // 和 getPassword() 一起序列化出去，等于把全站用户的 bcrypt 哈希交给管理员接口。
    @JsonIgnore
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

    // Spring Security 的 UserDetails 要从 getPassword() 拿密码；
    // 但这个 getter 同样会被 Jackson 识别成 JSON 的 "password" 字段，必须一起忽略。
    @JsonIgnore
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
