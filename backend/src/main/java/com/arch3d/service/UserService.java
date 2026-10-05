package com.arch3d.service;

import com.arch3d.dto.UserDTO;
import com.arch3d.entity.User;

public interface UserService {
    User findByUsername(String username);
    User findById(Integer id);
    User saveUser(User user);
    UserDTO getUserInfo(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    UserDTO updateUserInfo(String username, UserDTO userDTO);
    
    void deleteUser(Integer id);
    void resetPassword(Integer id);
}
