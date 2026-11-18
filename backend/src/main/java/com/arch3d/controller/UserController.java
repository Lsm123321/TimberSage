package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.UserDTO;
import com.arch3d.entity.User;
import com.arch3d.service.AvatarService;
import com.arch3d.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户信息管理接口")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;

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
            @Valid @RequestBody UserDTO userDTO) {
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

    /**
     * 上传当前用户头像。图片压到 512px 存 MinIO，旧头像顺手清掉。
     * 只更新 avatar 字段，其余资料由 PUT /users/me 负责。
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传当前用户头像", description = "上传图片并立即写入用户头像，返回可访问的 URL")
    public ResponseEntity<ApiResponse<String>> uploadMyAvatar(
            Authentication authentication,
            @Parameter(description = "头像图片，≤5MB，JPG/PNG/WebP/GIF/BMP")
            @RequestParam("file") MultipartFile file) {
        String username = authentication.getName();
        User user = userService.findByUsername(username);
        String oldAvatar = user.getAvatar();

        String avatarUrl;
        try {
            avatarUrl = avatarService.uploadAvatar(file);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("头像上传失败, username={}", username, e);
            return ResponseEntity.ok(ApiResponse.error(500, "头像上传失败，请稍后再试"));
        }

        UserDTO patch = new UserDTO();
        patch.setAvatar(avatarUrl);
        userService.updateUserInfo(username, patch);

        // 新图存好之后再删旧的，中途失败也不会把用户弄成没头像
        avatarService.deleteAvatar(oldAvatar);

        return ResponseEntity.ok(ApiResponse.success("头像已更新", avatarUrl));
    }
}
