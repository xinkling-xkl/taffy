package com.xk.service.impl;

import com.xk.dto.UserDTO;
import com.xk.entity.User;
import com.xk.mapper.UserMapper;
import com.xk.service.CacheClearService;
import com.xk.service.UserService;
import org.hibernate.validator.internal.util.stereotypes.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    @Lazy
    private CacheClearService cacheClearService;
    @Override
    public List<User> userList() {
        return userMapper.userList();
    }

    @Override
    public List<User> userList(int page, int size) {
        int offset = (page - 1) * size;
        return userMapper.userListByPage(offset, size);
    }

    @Override
    public int getUserCount() {
        return userMapper.getUserCount();
    }

    @Override
    public User getUserById(int id) {
        return userMapper.getUserById(id);
    }

    @Override
    public User addUser(User user) {
        if (user.getIsworking() == null) {
            user.setIsworking(0);
        }
        if (user.getTimepreference() == null || user.getTimepreference().trim().isEmpty()) {
            user.setTimepreference("[{\"day\":1,\"period\":\"morning\"},{\"day\":2,\"period\":\"morning\"},{\"day\":3,\"period\":\"morning\"},{\"day\":4,\"period\":\"morning\"},{\"day\":5,\"period\":\"morning\"}]");
        }
        int result = userMapper.addUser(user);
        if (result > 0) {

            return user;
        }
        return null;
    }

    @Override
    public String deleteUserById(int id) {
        Integer count = userMapper.deleteUserById(id);
        return count > 0 ? "success" : "fail";
    }

    @Override
    public String updateUser(User user) {
        Integer count = userMapper.updateUser(user);
        return count > 0 ? "success" : "fail";
    }

    @Override
    public User findByLoginInput(String input) {
        boolean isPhoneLogin = input != null && input.matches("^1[3-9]\\d{9}$");
        if (isPhoneLogin) {
            return userMapper.findByPhone(input);
        } else {
            return userMapper.findByName(input);
        }
    }

    @Override
    public String changePassword(int userId, String currentPassword, String newPassword) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            return "用户不存在";
        }

        if (currentPassword != null) {
            String storedPassword = user.getPassword();
            boolean oldPasswordMatch;
            if (storedPassword != null && storedPassword.startsWith("$2a$")) {
                oldPasswordMatch = passwordEncoder.matches(currentPassword, storedPassword);
            } else {
                oldPasswordMatch = currentPassword.equals(storedPassword);
            }
            if (!oldPasswordMatch) {
                return "旧密码不正确";
            }
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        Integer result = userMapper.updateUser(user);
        return result > 0 ? "success" : "密码修改失败";
    }

    @Override
    public UserDTO getUserDTOById(int id) {
        User user = userMapper.getUserById(id);
        return toUserDTO(user);
    }

    @Override
    public List<UserDTO> getUserDTOList(int page, int size) {
        List<User> users = userList(page, size);
        return users.stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO addUserDTO(UserDTO userDTO) {
        User user = toUserEntity(userDTO);
        User createdUser = addUser(user);
        return toUserDTO(createdUser);
    }

    @Override
    public UserDTO updateUserDTO(UserDTO userDTO) {
        User existingUser = userMapper.getUserById(userDTO.getId());
        if (existingUser == null) {
            return null;
        }
        if (userDTO.getName() != null && !userDTO.getName().trim().isEmpty()) {
            existingUser.setName(userDTO.getName());
        }
        if (userDTO.getPhone() != null && !userDTO.getPhone().trim().isEmpty()) {
            existingUser.setPhone(userDTO.getPhone());
        }
        if (userDTO.getAge() != null) {
            existingUser.setAge(userDTO.getAge());
        }
        if (userDTO.getImage() != null && !userDTO.getImage().trim().isEmpty()) {
            existingUser.setImage(userDTO.getImage());
        }
        String result = updateUser(existingUser);
        if (!"success".equals(result)) {
            throw new RuntimeException("更新用户信息失败");
        }
        return toUserDTO(existingUser);
    }

    @Override
    public void updateUserIdentity(int userId, String identity) {
        User user = userMapper.getUserById(userId);
        if (user != null) {
            user.setIdentity(identity);
            updateUser(user);
        }
    }

    @Override
    public User searchUser(String input, String auxiliary) {
        User user = userMapper.findByName(input);
        if (user != null) return user;
        user = userMapper.findByPhone(input);
        if (user != null) return user;
        if (auxiliary != null && !auxiliary.trim().isEmpty()) {
            user = userMapper.findByNumber(auxiliary);
            if (user != null) return user;
            user = userMapper.findByRname(auxiliary);
            if (user != null) return user;
            user = userMapper.findByIdcard(auxiliary);
            if (user != null) return user;
        }
        return null;
    }

    @Override
    public String updateTimePreference(int userId, String timepreference) {
            Integer result = userMapper.updateTimePreference(userId, timepreference);
            if (result > 0) {

                return "success";
            }
            return "更新时间偏好失败";
    }


    @Override
    public String updateWorkingStatus(int userId, Integer isworking) {
        Integer result = userMapper.updateWorkingStatus(userId, isworking);
        return result > 0 ? "success" : "更新工作状态失败";
    }

    @Override
    public List<User> getAvailableStudents() {
        return userMapper.getAvailableStudents();
    }

    @Override
    public int getAvailableStudentCount() {
        return userMapper.getAvailableStudentCount();
    }



    @Override
    public void freezeUser(int userId) {
        User user = userMapper.getUserById(userId);
        if (user != null) {
            user.setIsPenalty(1);          // 1 = 处罚中（冻结）
            user.setPenaltyEndTime(null);  // 无固定结束时间，等信用恢复
            userMapper.updateUser(user);
        }
    }

    @Override
    public void unfreezeUser(int userId) {
        User user = userMapper.getUserById(userId);
        if (user != null) {
            user.setIsPenalty(0);
            user.setPenaltyEndTime(null);
            userMapper.updateUser(user);
        }
    }

    @Override
    public void permanentlyBan(int userId) {
        User user = userMapper.getUserById(userId);
        if (user != null) {
            user.setIsPenalty(2);                         // 2 = 永久封禁
            user.setPenaltyEndTime(new Date(Long.MAX_VALUE));
            userMapper.updateUser(user);
        }
    }

    @Override
    public int incrementViolationCount(int userId) {
        User user = userMapper.getUserById(userId);
        if (user != null) {
            int currentCount = user.getViolationCount() == null ? 0 : user.getViolationCount();
            int newCount = currentCount + 1;
            user.setViolationCount(newCount);
            userMapper.updateUser(user);
            return newCount;
        }
        return 0;
    }

    @Override
    public List<User> getPenaltyUsers() {
        return userMapper.getPenaltyUsers();
    }



    private UserDTO toUserDTO(User user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setRname(user.getRname());
        dto.setNumber(user.getNumber());
        dto.setIdcard(user.getIdcard());
        dto.setAge(user.getAge());
        dto.setPhone(user.getPhone());
        dto.setImage(user.getImage());
        dto.setIdentity(user.getIdentity());
        dto.setCredit(user.getCredit());
        return dto;
    }

    private User toUserEntity(UserDTO dto) {
        if (dto == null) return null;
        User user = new User();
        if (dto.getId() != null) user.setId(dto.getId());
        user.setName(dto.getName());
        user.setRname(dto.getRname());
        user.setNumber(dto.getNumber());
        user.setIdcard(dto.getIdcard());
        user.setAge(dto.getAge() != null ? dto.getAge() : 0);
        user.setPhone(dto.getPhone());
        user.setImage(dto.getImage());
        user.setIdentity(dto.getIdentity());
        user.setCredit(dto.getCredit() != null ? dto.getCredit() : 0);
        return user;
    }
}
