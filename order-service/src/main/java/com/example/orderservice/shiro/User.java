package com.example.orderservice.shiro;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户实体（简化版）
 */
@Data
@AllArgsConstructor
public class User {
    private String username;  // 用户名
    private String password;  // 密码（密文）
    private String salt;      // 加密盐值
}