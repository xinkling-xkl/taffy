package com.xk.service;

import com.xk.dto.UserDTO;
import com.xk.entity.User;

import java.util.List;

public interface UserService {
    List<User> userList();
    List<User> userList(int page, int size);
    int getUserCount();
    User getUserById(int id);
    User addUser(User user);
    String deleteUserById(int id);
    String updateUser(User user);
    User findByLoginInput(String input);
    String changePassword(int userId, String currentPassword, String newPassword);
    UserDTO getUserDTOById(int id);
    List<UserDTO> getUserDTOList(int page, int size);
    UserDTO addUserDTO(UserDTO userDTO);
    UserDTO updateUserDTO(UserDTO userDTO);
    void updateUserIdentity(int userId, String identity);
    User searchUser(String input, String auxiliary);
    
    // 更新时间偏好
    String updateTimePreference(int userId, String timepreference);

    // 更新工作状态
    String updateWorkingStatus(int userId, Integer isworking);

    // 获取空闲学生列表
    List<User> getAvailableStudents();

    // 获取空闲学生数量
    int getAvailableStudentCount();
    void freezeUser(int userId);
    void unfreezeUser(int userId);
    void permanentlyBan(int userId);
    int incrementViolationCount(int userId);
    
    // 获取被处罚且信用分低于50的用户
    List<User> getPenaltyUsers();
}
