package com.arch3d.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Integer id;
    private String username;
    private String email;
    private String nickname;

    // 资料更新接口直接复用这个 DTO，头像地址必须在这里做协议白名单，
    // 否则攻击者可以把 avatar 改成 javascript: 载荷存进用户表。
    @Pattern(regexp = "^$|^(https?://|/(?!/)).*", message = "头像地址不合法")
    private String avatar;

    private String role;
}
