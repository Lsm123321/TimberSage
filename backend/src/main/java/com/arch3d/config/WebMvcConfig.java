package com.arch3d.config;

import com.arch3d.interceptor.AuthRateLimitInterceptor;
import com.arch3d.interceptor.GuestViewRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthRateLimitInterceptor authRateLimitInterceptor;
    private final GuestViewRateLimitInterceptor guestViewRateLimitInterceptor;

    public WebMvcConfig(AuthRateLimitInterceptor authRateLimitInterceptor,
                        GuestViewRateLimitInterceptor guestViewRateLimitInterceptor) {
        this.authRateLimitInterceptor = authRateLimitInterceptor;
        this.guestViewRateLimitInterceptor = guestViewRateLimitInterceptor;
    }

    // 把本地上传目录映射成静态资源，早期存的本地文件还能直接访问
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:uploads/");
    }

    // 认证接口按 IP 限流，防机器批量注册和登录爆破
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns("/api/auth/**");

        // 模型文件预览接口按 IP 限流：游客开放预览后，防遍历 modelId 批量拖库
        registry.addInterceptor(guestViewRateLimitInterceptor)
                .addPathPatterns("/api/models/*/file-url", "/api/public/models/*/file-url");
    }
}
