package com.example.orderservice.shiro;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试接口
 */
@RestController
public class TestController {

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        // 1. 获取当前用户主体
        Subject subject = SecurityUtils.getSubject();

        // 2. 封装用户输入的账号密码
        UsernamePasswordToken token = new UsernamePasswordToken(username, password);

        try {
            // 3. 登录认证（会调用CustomRealm的doGetAuthenticationInfo方法）
            subject.login(token);
            return "登录成功！用户名：" + username;
        } catch (Exception e) {
            return "登录失败：" + e.getMessage();
        }
    }

    /**
     * 管理员专属接口（需要admin角色）
     */
    @GetMapping("/admin/test")
    public String adminTest() {
        return "管理员接口访问成功！";
    }

    /**
     * 普通用户接口（需要user角色）
     */
    @GetMapping("/user/test")
    public String userTest() {
        return "普通用户接口访问成功！";
    }

    /**
     * 未授权提示
     */
    @GetMapping("/unauthorized")
    public String unauthorized() {
        return "权限不足！";
    }

    /**
     * 退出登录
     */
    @GetMapping("/logout")
    public String logout() {
        SecurityUtils.getSubject().logout();
        return "退出登录成功！";
    }
}