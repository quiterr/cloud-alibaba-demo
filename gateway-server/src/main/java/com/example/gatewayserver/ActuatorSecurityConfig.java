package com.example.gatewayserver;

import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity // 网关必须加这个
public class ActuatorSecurityConfig {

    // 密码加密
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 登录账号（生产可放Nacos）
    @Bean
    public MapReactiveUserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails user = User.withUsername("admin")
                .password(encoder.encode("Admin@123"))
                .roles("ADMIN")
                .build();

        return new MapReactiveUserDetailsService(user);
    }

    // 网关 Reactive 安全配置（核心）
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchange -> exchange
                        // Actuator 规则
                        .matchers(EndpointRequest.to("health", "info", "prometheus")).permitAll()
                        .matchers(EndpointRequest.toAnyEndpoint()).hasRole("ADMIN")
                        // 其他接口全部放行
                        .anyExchange().permitAll()
                )
                .httpBasic();

        return http.build();
    }
}