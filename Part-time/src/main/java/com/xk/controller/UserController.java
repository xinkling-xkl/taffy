package com.xk.controller;

import com.xk.common.Result;
import com.xk.dto.ChangePasswordRequestDTO;
import com.xk.dto.LoginRequestDTO;
import com.xk.dto.RegisterRequestDTO;
import com.xk.dto.UserDTO;
import com.xk.entity.User;
import com.xk.exception.BusinessException;
import com.xk.service.UserService;
import com.xk.util.FileUtils;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final Key secretKey;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Value("${file.upload.path}")
    private String uploadPath;

    public UserController(Key secretKey, PasswordEncoder passwordEncoder) {
        this.secretKey = secretKey;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/{id}/avatar")
    public Result<String> uploadAvatar(@PathVariable("id") int id, @RequestParam("file") MultipartFile file) {
        try {
            UserDTO userDTO = userService.getUserDTOById(id);
            if (userDTO == null) {
                throw new BusinessException("用户不存在");
            }
            if (userDTO.getImage() != null && !userDTO.getImage().isEmpty()) {
                FileUtils.deleteFile(userDTO.getImage(), uploadPath);
            }
            String imagePath = FileUtils.uploadImage(file, uploadPath, "avatar");
            userDTO.setImage(imagePath);
            userService.updateUserDTO(userDTO);
            return Result.success("头像上传成功", imagePath);
        } catch (IOException e) {
            throw new BusinessException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @GetMapping
    public Result<Map<String, Object>> userList(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        List<UserDTO> users = userService.getUserDTOList(page, size);
        int total = userService.getUserCount();
        return Result.success(Map.of("users", users, "total", total));
    }

    @GetMapping("/available")
    public Result<Map<String, Object>> getAvailableStudents() {
        List<User> students = userService.getAvailableStudents();
        int total = userService.getAvailableStudentCount();
        return Result.success(Map.of("data", students, "total", total));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> getUser(@PathVariable("id") int id) {
        UserDTO user = userService.getUserDTOById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }

    @PostMapping
    public Result<UserDTO> addUser(@RequestBody UserDTO userDTO) {
        UserDTO createdUser = userService.addUserDTO(userDTO);
        return Result.success(createdUser);
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable("id") int id) {
        String result = userService.deleteUserById(id);
        return Result.success(result);
    }

    @PostMapping("/{id}")
    public Result<UserDTO> updateUser(@PathVariable("id") int id, @RequestBody UserDTO userDTO) {
        userDTO.setId(id);
        userService.updateUserDTO(userDTO);
        return Result.success(userService.getUserDTOById(id));
    }

    @PostMapping("/{id}/change-password")
    public Result<?> changePassword(@PathVariable("id") int id, @RequestBody ChangePasswordRequestDTO passwordData) {
        String currentPassword = passwordData.getOldPassword();
        String newPassword = passwordData.getNewPassword();
        if (currentPassword == null || currentPassword.trim().isEmpty()) {
            throw new BusinessException("旧密码不能为空");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new BusinessException("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }
        String result = userService.changePassword(id, currentPassword.trim(), newPassword.trim());
        if (!"success".equals(result)) {
            throw new BusinessException(result);
        }
        return Result.successMsg("密码修改成功");
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequestDTO loginRequest) {
        if (loginRequest == null || loginRequest.getInput() == null || loginRequest.getPassword() == null) {
            throw new BusinessException("用户名/手机号和密码不能为空");
        }
        String input = loginRequest.getInput().trim();
        String rawPassword = loginRequest.getPassword().trim();

        User user = userService.findByLoginInput(input);
        if (user == null) {
            throw new BusinessException("用户名/手机号或密码错误");
        }

        if (user.getIsPenalty() != null && user.getIsPenalty() >= 1) {
            throw new BusinessException("该账号已被冻结或封禁");
        }

        boolean passwordMatch = false;
        String storedPassword = user.getPassword();
        if (storedPassword != null && storedPassword.startsWith("$2a$")) {
            passwordMatch = passwordEncoder.matches(rawPassword, storedPassword);
        } else {
            passwordMatch = rawPassword.equals(storedPassword);
            if (passwordMatch) {
                user.setPassword(passwordEncoder.encode(rawPassword));
                userService.updateUser(user);
            }
        }

        if (!passwordMatch) {
            throw new BusinessException("用户名/手机号或密码错误");
        }

        String token = Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("userId", user.getId())
                .claim("identity", user.getIdentity())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 36500L * 24 * 60 * 60 * 1000))
                .signWith(secretKey)
                .compact();
        user.setPassword(null);
        return Result.success("登录成功", Map.of("token", token, "user", user));
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        return Result.successMsg("登出成功");
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequestDTO registerRequest) {
        if (registerRequest == null || registerRequest.getInput() == null || registerRequest.getPassword() == null) {
            throw new BusinessException("用户名/手机号和密码不能为空");
        }
        String input = registerRequest.getInput().trim();
        String password = registerRequest.getPassword().trim();
        boolean isPhoneRegister = input.matches("^1[3-9]\\d{9}$");
        User user = new User();
        if (isPhoneRegister) {
            User existing = userService.searchUser(input, null);
            if (existing != null) {
                throw new BusinessException("该手机号已注册账号");
            }
            user.setPhone(input);
            user.setName("用户" + input.substring(input.length() - 4));
        } else {
            user.setName(input);
        }
        user.setPassword(passwordEncoder.encode(password));
        user.setAge(18);
        user.setIdentity("未认证");
        user.setCredit(100);
        user.setIsworking(0);
        user.setTimepreference("[{\"day\":1,\"period\":1},{\"day\":1,\"period\":2},{\"day\":1,\"period\":3},{\"day\":2,\"period\":1},{\"day\":2,\"period\":2},{\"day\":2,\"period\":3},{\"day\":3,\"period\":1},{\"day\":3,\"period\":2},{\"day\":3,\"period\":3},{\"day\":4,\"period\":1},{\"day\":4,\"period\":2},{\"day\":4,\"period\":3},{\"day\":5,\"period\":1},{\"day\":5,\"period\":2},{\"day\":5,\"period\":3}]");
        User createdUser = userService.addUser(user);
        if (createdUser == null) {
            throw new BusinessException("注册失败，请稍后重试");
        }
        return Result.success("注册成功", Map.of("user", createdUser));
    }

    @PostMapping("/search")
    public Result<Map<String, Object>> searchUser(@RequestBody Map<String, String> searchRequest) {
        String input = searchRequest.get("input");
        String auxiliary = searchRequest.get("auxiliary");
        if (input == null || input.trim().isEmpty()) {
            throw new BusinessException("请输入用户名或手机号");
        }
        User user = userService.searchUser(input, auxiliary);
        if (user == null) {
            return Result.error("未找到您的用户信息");
        }
        return Result.success(Map.of("user", user));
    }

    @PostMapping("/reset-password")
    public Result<?> resetPassword(@RequestBody Map<String, Object> resetRequest) {
        Integer userId = (Integer) resetRequest.get("userId");
        String newPassword = (String) resetRequest.get("newPassword");
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new BusinessException("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }
        String resetResult = userService.changePassword(userId, null, newPassword);
        if (!"success".equals(resetResult)) {
            throw new BusinessException(resetResult);
        }
        return Result.successMsg("密码重置成功");
    }

    @PostMapping("/time-preference")
    public Result<?> updateTimePreference(@RequestBody Map<String, Object> request) {
        Integer userId = (Integer) request.get("userId");
        String timepreference = (String) request.get("timepreference");
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (timepreference == null || timepreference.trim().isEmpty()) {
            throw new BusinessException("时间偏好不能为空");
        }
        String result = userService.updateTimePreference(userId, timepreference);
        if (!"success".equals(result)) {
            throw new BusinessException(result);
        }
        return Result.successMsg("时间偏好更新成功");
    }

    @PostMapping("/working-status")
    public Result<?> updateWorkingStatus(@RequestBody Map<String, Object> request) {
        Integer userId = (Integer) request.get("userId");
        Integer isworking = (Integer) request.get("isworking");
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (isworking == null || (isworking != 0 && isworking != 1)) {
            throw new BusinessException("工作状态必须为0（未工作）或1（工作中）");
        }
        String result = userService.updateWorkingStatus(userId, isworking);
        if (!"success".equals(result)) {
            throw new BusinessException(result);
        }
        return Result.successMsg("工作状态更新成功");
    }

    @PutMapping("/{id}/credit")
    public Result<?> updateUserCredit(@PathVariable("id") int id, @RequestBody Map<String, Object> request) {
        Integer credit = (Integer) request.get("credit");
        if (credit == null) {
            throw new BusinessException("信用分不能为空");
        }
        if (credit < 0 || credit > 100) {
            throw new BusinessException("信用分必须在0-100之间");
        }
        User user = userService.getUserById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setCredit(credit);
        if (credit >= 50 && user.getIsPenalty() != null && user.getIsPenalty() == 1) {
            user.setIsPenalty(0);
            user.setPenaltyEndTime(null);
        }
        String result = userService.updateUser(user);
        if (!"success".equals(result)) {
            throw new BusinessException(result);
        }
        return Result.successMsg("信用分更新成功");
    }

    @PostMapping("/{id}/reset-time-preference")
    public Result<?> resetTimePreference(@PathVariable int id) {
        User user = userService.getUserById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setTimepreference(null);
        userService.updateUser(user);
        return Result.successMsg("时间偏好已重置");
    }
}
