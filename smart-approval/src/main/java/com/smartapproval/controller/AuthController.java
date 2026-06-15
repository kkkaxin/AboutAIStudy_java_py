package com.smartapproval.controller;

import com.smartapproval.dto.Result;
import com.smartapproval.entity.User;
import com.smartapproval.repository.UserRepository;
import com.smartapproval.security.JwtUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String realName = body.getOrDefault("realName", username);
        String department = body.getOrDefault("department", "技术部");
        String roleStr = body.getOrDefault("role", "EMPLOYEE");

        if (userRepository.findByUsername(username).isPresent()) {
            return Result.error("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(realName);
        user.setDepartment(department);
        user.setRole(User.Role.valueOf(roleStr.toUpperCase()));

        userRepository.save(user);

        String token = jwtUtils.generateToken(user.getId(), username, roleStr);

        return Result.success(Map.of(
                "token", token,
                "userId", user.getId(),
                "username", username,
                "role", roleStr,
                "realName", realName
        ));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        User user = userRepository.findByUsername(username)
                .orElse(null);

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }

        String token = jwtUtils.generateToken(
                user.getId(), username, user.getRole().name());

        return Result.success(Map.of(
                "token", token,
                "userId", user.getId(),
                "username", username,
                "role", user.getRole().name(),
                "realName", user.getRealName(),
                "department", user.getDepartment()
        ));
    }
}
