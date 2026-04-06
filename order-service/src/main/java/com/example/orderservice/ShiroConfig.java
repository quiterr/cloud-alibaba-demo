package com.example.orderservice;

import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShiroConfig {

    // 1. 登录认证 Realm（演示用，你以后换DB即可）
    @Bean
    public Realm realm() {
        SimpleAccountRealm realm = new SimpleAccountRealm();
        // 模拟账号：admin / 123456
        realm.addAccount("admin", "123456", "user");
        return realm;
    }

    // 2. 过滤器链配置
    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chain = new DefaultShiroFilterChainDefinition();

        // 放行登录接口
        chain.addPathDefinition("/login", "anon");
        chain.addPathDefinition("/doLogin", "anon");

        // 其他都需要登录
        chain.addPathDefinition("/**", "authc");
        return chain;
    }
}
