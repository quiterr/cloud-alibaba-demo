package com.example.orderservice;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.subject.Subject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    // 测试接口（必须登录）
    @GetMapping("/index")
    public String index() {
        Subject subject = SecurityUtils.getSubject();
        return "登录成功！当前用户：" + subject.getPrincipal();
    }

    // 登录页面提示
    @GetMapping("/login")
    public String loginPage() {
        return "请 POST 访问 /doLogin?username=admin&password=123456";
    }

    // 登录接口
    @PostMapping("/doLogin")
    public String doLogin(String username, String password) {
        Subject subject = SecurityUtils.getSubject();

        if (!subject.isAuthenticated()) {
            UsernamePasswordToken token = new UsernamePasswordToken(username, password);
            try {
                subject.login(token);
                return "登录成功！";
            } catch (UnknownAccountException e) {
                return "用户不存在";
            } catch (IncorrectCredentialsException e) {
                return "密码错误";
            } catch (LockedAccountException e) {
                return "账号锁定";
            } catch (AuthenticationException e) {
                return "认证失败：" + e.getMessage();
            }
        }
        return "已登录";
    }

    // 退出
    @GetMapping("/logout")
    public String logout() {
        SecurityUtils.getSubject().logout();
        return "已退出登录";
    }
}
