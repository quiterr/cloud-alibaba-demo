package com.example.userservice;

import com.example.common.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        // 模拟数据库查询
        return new User(id, "张三", 20);
    }
}