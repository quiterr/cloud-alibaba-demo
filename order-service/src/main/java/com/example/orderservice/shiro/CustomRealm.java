package com.example.orderservice.shiro;

import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.lang.util.ByteSource;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.stereotype.Component;


import java.util.HashSet;
import java.util.Set;

/**
 * 自定义Realm，实现认证和授权逻辑
 */
@Component
public class CustomRealm extends AuthorizingRealm {

    /**
     * 授权：获取用户角色/权限
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        // 从主体中获取用户名
        String username = (String) principals.getPrimaryPrincipal();

        // 模拟从数据库查询用户权限（实际项目需查库）
        Set<String> permissions = new HashSet<>();
        Set<String> roles = new HashSet<>();
        if ("admin".equals(username)) {
            roles.add("admin");          // 管理员角色
            permissions.add("user:*");   // 所有用户操作权限
        } else if ("user".equals(username)) {
            roles.add("user");           // 普通用户角色
            permissions.add("user:view");// 仅查看权限
        }

        // 封装授权信息
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setRoles(roles);
        info.setStringPermissions(permissions);
        return info;
    }

    /**
     * 认证：校验用户名密码
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        // 1. 获取用户输入的用户名
        UsernamePasswordToken upToken = (UsernamePasswordToken) token;
        String username = upToken.getUsername();

        // 2. 模拟从数据库查询用户（实际项目需查库）
        User user = null;
        if ("admin".equals(username)) {
            user = new User("admin", "123456", "admin");
        } else if ("user".equals(username)) {
            user = new User("user", "123456", "user");
        }

        // 3. 用户不存在则抛出异常
        if (user == null) {
            throw new UnknownAccountException("用户名不存在");
        }

        // 4. 封装认证信息（密码会自动和用户输入的密码对比）
        return new SimpleAuthenticationInfo(
                user.getUsername(),       // 主体（用户标识）
                user.getPassword(),       // 数据库密码（密文）
                ByteSource.Util.bytes(user.getSalt()), // 加密盐值
                getName()                 // Realm名称
        );
    }
}