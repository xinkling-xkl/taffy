package com.xk.mapper;

import com.xk.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    List<User> userList();
    List<User> userListByPage(@Param("offset") int offset, @Param("size") int size);
    int getUserCount();
    User getUserById(int id);
    int addUser(User user);
    Integer deleteUserById(int id);
    Integer updateUser(User user);
    User findByPhoneAndPassword(@Param("phone") String phone, @Param("password") String password);
    User findByNameAndPassword(@Param("name") String name, @Param("password") String password);
    User findByName(@Param("name") String name);
    User findByPhone(@Param("phone") String phone);
    User findByNumber(@Param("number") String number);
    User findByRname(@Param("rname") String rname);
    User findByIdcard(@Param("idcard") String idcard);
    
    // 更新时间偏好
    Integer updateTimePreference(@Param("userId") int userId, @Param("timepreference") String timepreference);
    
    // 更新工作状态
    Integer updateWorkingStatus(@Param("userId") int userId, @Param("isworking") Integer isworking);

    // 获取空闲学生列表
    List<User> getAvailableStudents();

    // 获取空闲学生数量
    int getAvailableStudentCount();
    
    // 获取被处罚且信用分低于50的用户
    List<User> getPenaltyUsers();


    List<User> getAllStudents();
}
