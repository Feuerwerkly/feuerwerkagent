package com.example.feuerwerkagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.mapper.ShopUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 商店用户服务：注册/登录/资料/密码
 */
@Service
public class ShopUserService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private ShopUserMapper userMapper;

    @Autowired
    private ShopTokenManager tokenManager;

    /**
     * 注册
     */
    public Map<String, Object> register(String username, String password, String nickname) {
        if (username == null || !username.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{2,20}$")) {
            throw new IllegalArgumentException("用户名需2-20位字母、数字、下划线或中文");
        }
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new IllegalArgumentException("密码长度需6-32位");
        }

        Long exists = userMapper.selectCount(new QueryWrapper<ShopUser>().eq("username", username));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("用户名已被注册");
        }

        ShopUser user = new ShopUser();
        user.setUsername(username);
        user.setPassword(PASSWORD_ENCODER.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        user.setRole(ShopUser.ROLE_USER);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        return buildLoginResult(user);
    }

    /**
     * 登录
     */
    public Map<String, Object> login(String username, String password) {
        ShopUser user = userMapper.selectOne(new QueryWrapper<ShopUser>().eq("username", username));
        if (user == null || !PASSWORD_ENCODER.matches(password == null ? "" : password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已被禁用");
        }
        return buildLoginResult(user);
    }

    /**
     * 更新个人资料
     */
    public ShopUser updateProfile(ShopUser current, String nickname, String phone, String email) {
        if (nickname != null && !nickname.isBlank()) {
            current.setNickname(nickname);
        }
        if (phone != null) {
            current.setPhone(phone.trim());
        }
        if (email != null) {
            current.setEmail(email.trim());
        }
        current.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(current);
        return current;
    }

    /**
     * 修改密码
     */
    public void changePassword(ShopUser current, String oldPassword, String newPassword) {
        if (!PASSWORD_ENCODER.matches(oldPassword == null ? "" : oldPassword, current.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 32) {
            throw new IllegalArgumentException("新密码长度需6-32位");
        }
        current.setPassword(PASSWORD_ENCODER.encode(newPassword));
        current.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(current);
    }

    /**
     * 构造登录/注册返回：token + 用户信息（不含密码）
     */
    private Map<String, Object> buildLoginResult(ShopUser user) {
        String token = tokenManager.issueToken(user.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", sanitize(user));
        return result;
    }

    /**
     * 去除密码等敏感字段
     */
    public static Map<String, Object> sanitize(ShopUser user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        map.put("phone", user.getPhone());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("createTime", user.getCreateTime());
        return map;
    }
}
