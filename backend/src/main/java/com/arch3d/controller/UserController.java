package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.UserDTO;
import com.arch3d.entity.User;
import com.arch3d.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户信息管理接口")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "获取已登录用户详细信息")
    public ResponseEntity<ApiResponse<UserDTO>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未认证"));
        }
        String username = authentication.getName();
        UserDTO userDTO = userService.getUserInfo(username);
        return ResponseEntity.ok(ApiResponse.success(userDTO));
    }

    @PutMapping("/me")
    @Operation(summary = "更新当前用户信息", description = "更新已登录用户信息")
    public ResponseEntity<ApiResponse<UserDTO>> updateCurrentUser(
            Authentication authentication,
            @RequestBody UserDTO userDTO) {
        String username = authentication.getName();
        UserDTO updated = userService.updateUserInfo(username, userDTO);
        return ResponseEntity.ok(ApiResponse.success("更新成功", updated));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取用户信息", description = "根据ID获取用户信息")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable Integer id) {
        User user = userService.findById(id);
        UserDTO userDTO = UserDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .avatar(user.getAvatar())
            .role(user.getRole())
            .build();
        return ResponseEntity.ok(ApiResponse.success(userDTO));
    }
}
