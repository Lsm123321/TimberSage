package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.AuthResponse;
import com.arch3d.dto.LoginRequest;
import com.arch3d.dto.RegisterRequest;
import com.arch3d.dto.CaptchaResponse;
import com.arch3d.entity.User;
import com.arch3d.repository.UserRepository;
import com.arch3d.service.CaptchaService;
import com.arch3d.util.JwtTokenUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录和注册接口")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    @Operation(summary = "获取登录验证码", description = "返回验证码ID和SVG图像")
    public ResponseEntity<ApiResponse<CaptchaResponse>> captcha() {
        return ResponseEntity.ok(ApiResponse.success(captchaService.generateCaptcha()));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            boolean captchaOk = captchaService.verifyAndConsume(loginRequest.getCaptchaId(), loginRequest.getCaptchaCode());
            if (!captchaOk) {
                throw new RuntimeException("验证码错误或已过期");
            }

            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

            String token = jwtTokenUtil.generateToken(user.getUsername(), user.getRole());

            AuthResponse response = AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BadCredentialsException e) {
            throw e; // 让全局异常处理器处理
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            throw new RuntimeException("登录失败，请检查用户名和密码");
        }
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户，返回JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            if (userRepository.existsByUsername(registerRequest.getUsername())) {
                throw new RuntimeException("用户名已存在");
            }

            if (registerRequest.getEmail() != null && userRepository.existsByEmail(registerRequest.getEmail())) {
                throw new RuntimeException("邮箱已被使用");
            }

            User user = User.builder()
                .username(registerRequest.getUsername())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .email(registerRequest.getEmail())
                .avatar(registerRequest.getAvatar())
                .role("user") // 默认角色为普通用户
                .build();

            userRepository.save(user);

            String token = jwtTokenUtil.generateToken(user.getUsername(), user.getRole());

            AuthResponse response = AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage());
            throw e;
        }
    }
}
