package com.example.orderservice.shiro;

import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Apache Shiro 核心配置类
 * 用于集成 Shiro 权限框架到 Spring Boot 3.x 应用
 */
@Configuration
public class ShiroConfig {

    // 注入自定义Realm（替代原有的SimpleAccountRealm）
    @Bean
    public CustomRealm customRealm() {
        return new CustomRealm();
    }

    // 核心修复：使用DefaultWebSecurityManager（实现WebSecurityManager接口）
    @Bean
    public DefaultWebSecurityManager securityManager() {
        DefaultWebSecurityManager manager = new DefaultWebSecurityManager();
        manager.setRealm(customRealm()); // 关联自定义Realm
        return manager;
    }

    @Bean("shiroFilter")
    public ShiroFilterFactoryBean shiroFilterFactoryBean() {
        ShiroFilterFactoryBean filter = new ShiroFilterFactoryBean();
        filter.setSecurityManager(securityManager()); // 传入Web版SecurityManager

        // 配置跳转路径
        filter.setLoginUrl("/login");
        filter.setSuccessUrl("/index");
        filter.setUnauthorizedUrl("/unauthorized");

        // 配置拦截规则
        Map<String, String> chain = new LinkedHashMap<>();
        chain.put("/login", "anon");        // 登录接口匿名访问
        chain.put("/doLogin", "anon");
        chain.put("/css/**", "anon");
        chain.put("/js/**", "anon");
        chain.put("/favicon.ico", "anon");
        chain.put("/admin/test", "roles[admin]"); // 管理员角色才能访问
        chain.put("/user/test", "roles[user]");   // 普通用户角色才能访问
        chain.put("/**", "authc");          // 其他路径需要认证

        filter.setFilterChainDefinitionMap(chain);
        return filter;
    }

    // 移除手动注册FilterRegistrationBean的代码（ShiroFilterFactoryBean会自动处理，避免重复）
}