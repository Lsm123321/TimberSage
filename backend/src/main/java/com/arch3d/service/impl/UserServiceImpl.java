package com.arch3d.service.impl;

import com.arch3d.dto.UserDTO;
import com.arch3d.entity.User;
import com.arch3d.repository.AiChatHistoryRepository;
import com.arch3d.repository.CommentLikeRepository;
import com.arch3d.repository.ContributionApplyRepository;
import com.arch3d.repository.DownloadLogRepository;
import com.arch3d.repository.ModelCommentRepository;
import com.arch3d.repository.ModelFeedbackRepository;
import com.arch3d.repository.UserMaterialPresetRepository;
import com.arch3d.repository.UserModelFavoriteRepository;
import com.arch3d.repository.UserModelLikeRepository;
import com.arch3d.repository.UserRepository;
import com.arch3d.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CommentLikeRepository commentLikeRepository;
    private final ModelCommentRepository modelCommentRepository;
    private final ModelFeedbackRepository modelFeedbackRepository;
    private final ContributionApplyRepository contributionApplyRepository;
    private final UserMaterialPresetRepository userMaterialPresetRepository;
    private final UserModelLikeRepository userModelLikeRepository;
    private final UserModelFavoriteRepository userModelFavoriteRepository;
    private final AiChatHistoryRepository aiChatHistoryRepository;
    private final DownloadLogRepository downloadLogRepository;

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("用户不存在: " + username));
    }

    @Override
    public User findById(Integer id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @Override
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public UserDTO getUserInfo(String username) {
        User user = findByUsername(username);
        return convertToDTO(user);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return email != null && userRepository.existsByEmail(email);
    }

    @Override
    public UserDTO updateUserInfo(String username, UserDTO userDTO) {
        User user = findByUsername(username);

        if (userDTO.getNickname() != null) {
            user.setNickname(userDTO.getNickname());
        }
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail());
        }
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar());
        }

        User updated = userRepository.save(user);
        return convertToDTO(updated);
    }

    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .avatar(user.getAvatar())
            .role(user.getRole())
            .build();
    }

    @Override
    @Transactional
    public void deleteUser(Integer id) {
        User user = findById(id);
        commentLikeRepository.deleteByUserId(id);
        modelCommentRepository.deleteByUserId(id);
        modelFeedbackRepository.deleteByUserId(id);
        contributionApplyRepository.deleteByUserId(id);
        userMaterialPresetRepository.deleteByUserId(id);
        userModelLikeRepository.deleteByUserId(id);
        userModelFavoriteRepository.deleteByUserId(id);
        aiChatHistoryRepository.deleteByUserId(id);
        downloadLogRepository.deleteByUserId(id);
        userRepository.delete(user);
    }

    @Override
    public void resetPassword(Integer id) {
        User user = findById(id);
        // 固定重置成 123456，演示项目图省事；要上生产得改成随机密码+通知用户
        final String defaultPassword = "123456";
        user.setPasswordHash(passwordEncoder.encode(defaultPassword));
        userRepository.save(user);
    }
}
