package com.smartdoc.controller;

import com.smartdoc.dto.Result;
import com.smartdoc.entity.User;
import com.smartdoc.repository.UserRepository;
import com.smartdoc.utils.JwtUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElse(null);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return Result.fail(401, "用户名或密码错误");
        }
        String token = jwtUtils.generateToken(user.getId(), user.getRole());
        return Result.success(Map.of(
                "token", token,
                "userId", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole()
        ));
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            return Result.fail("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setEmail(req.getEmail());
        user.setRole("USER");
        userRepository.save(user);
        return Result.success("注册成功");
    }

    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    static class RegisterRequest {
        private String username;
        private String password;
        private String email;
    }
}
