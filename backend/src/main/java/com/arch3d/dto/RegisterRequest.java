package com.arch3d.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    // 用户名只允许中文、字母、数字、下划线和连字符；拒绝空格/引号/JNDI/SQL片段等注入载荷
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度必须在3-64之间")
    @Pattern(regexp = "[\\u4e00-\\u9fa5A-Za-z0-9_-]+", message = "用户名只能包含中文、字母、数字、下划线或连字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度至少6位")
    private String password;

    @Email(message = "邮箱格式不正确")
    private String email;

    // 头像只允许 http(s) 绝对地址或以单个 / 开头的站内路径；
    // 直接拒绝 javascript:/data: 等可执行协议，避免资料页存储型 XSS。
    @Pattern(regexp = "^$|^(https?://|/(?!/)).*", message = "头像地址不合法")
    private String avatar;
}
