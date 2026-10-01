package com.arch3d.config;

import com.arch3d.entity.BuildingCategory;
import com.arch3d.entity.User;
import com.arch3d.repository.BuildingCategoryRepository;
import com.arch3d.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BuildingCategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 启动时保证 admin/testuser 和默认分类存在。
     * 注意：每次启动都会把这两个账号的密码重置回 123456，演示方便但别上生产。
     */
    @Override
    public void run(String... args) {
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("123456"))
                .email("admin@arch3d.com")
                .nickname("管理员")
                .role("admin")
                .build();
            userRepository.save(admin);
            log.info("Default admin user created: username=admin");
        } else {
            // 每次启动都刷一遍默认值，防止有人改乱了登不进去
            admin.setPasswordHash(passwordEncoder.encode("123456"));
            admin.setEmail("admin@arch3d.com");
            admin.setNickname("管理员");
            admin.setRole("admin");
            userRepository.save(admin);
            log.info("Default admin user updated: username=admin");
        }

        User testUser = userRepository.findByUsername("testuser").orElse(null);
        if (testUser == null) {
            testUser = User.builder()
                .username("testuser")
                .passwordHash(passwordEncoder.encode("123456"))
                .email("testuser@arch3d.com")
                .nickname("测试用户")
                .role("user")
                .build();
            userRepository.save(testUser);
            log.info("Default test user created: username=testuser");
        } else {
            testUser.setPasswordHash(passwordEncoder.encode("123456"));
            testUser.setEmail("testuser@arch3d.com");
            testUser.setNickname("测试用户");
            testUser.setRole("user");
            userRepository.save(testUser);
            log.info("Default test user updated: username=testuser");
        }

        initializeDefaultCategories();
    }

    private void initializeDefaultCategories() {
        upsertCategory("bridge", "古桥梁", 0);
        upsertCategory("palace", "宫殿建筑", 1);
        upsertCategory("temple", "寺庙建筑", 2);
        upsertCategory("garden", "园林建筑", 3);
        upsertCategory("dwelling", "民居建筑", 4);
        upsertCategory("tower", "塔楼建筑", 5);
    }

    private void upsertCategory(String code, String name, Integer sortOrder) {
        BuildingCategory category = categoryRepository.findByCategoryCode(code);
        if (category == null) {
            category = new BuildingCategory();
            category.setCategoryCode(code);
            log.info("Default category created: {} / {}", code, name);
        }
        category.setCategoryName(name);
        category.setSortOrder(sortOrder);
        categoryRepository.save(category);
    }
}
