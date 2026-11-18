package com.arch3d.config;

import com.arch3d.entity.BuildingCategory;
import com.arch3d.entity.User;
import com.arch3d.repository.BuildingCategoryRepository;
import com.arch3d.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
     * 仅用于首次创建 admin/testuser 的初始密码。
     * 生产环境应通过 APP_ADMIN_INIT_PASSWORD 注入，并在首次登录后立即修改。
     */
    @Value("${app.admin.init-password:123456}")
    private String initialPassword;

    /**
     * 启动时保证 admin/testuser 和默认分类存在。
     *
     * 重要：只在账号缺失时创建，绝不覆盖已有账号的密码和角色。
     * 旧逻辑每次启动都重置成 123456，等于给公网部署留了一个永久后门：
     * 管理员改完密码后只要重启服务就会被打回默认口令。
     */
    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode(initialPassword))
                .email("admin@arch3d.com")
                .nickname("管理员")
                .role("admin")
                .build();
            userRepository.save(admin);
            log.warn("Default admin user created: username=admin，请登录后立即修改初始密码");
        }

        if (userRepository.findByUsername("testuser").isEmpty()) {
            User testUser = User.builder()
                .username("testuser")
                .passwordHash(passwordEncoder.encode(initialPassword))
                .email("testuser@arch3d.com")
                .nickname("测试用户")
                .role("user")
                .build();
            userRepository.save(testUser);
            log.info("Default test user created: username=testuser");
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
