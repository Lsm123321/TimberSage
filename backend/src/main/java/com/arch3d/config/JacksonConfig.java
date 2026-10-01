package com.arch3d.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化全局配置。
 *
 * 实体里的 @ManyToOne 默认懒加载，查出来的对象实际是 Hibernate 的 ByteBuddy
 * 代理（比如 ContributionApply.user）。Jackson 不认识代理，直接序列化就抛
 * "Type definition error: ByteBuddyInterceptor"——管理后台的贡献者申请列表
 * 就是这么挂的（表里有数据后才暴露）。
 *
 * 注册 Hibernate6Module 并开 FORCE_LAZY_LOADING：遇到代理就取真实实体序列化，
 * 避免 user 字段变 null；同时关掉 FAIL_ON_EMPTY_BEANS 兜底，
 * 个别代理漏网时宁可字段为 null 也不让整个接口 500。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer hibernateProxyJacksonCustomizer() {
        return builder -> {
            Hibernate6Module module = new Hibernate6Module()
                    .enable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
            builder.modulesToInstall(module);
            builder.featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        };
    }
}
