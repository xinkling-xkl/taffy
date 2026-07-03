package com.xk.service;

import com.xk.entity.User;

import java.time.LocalDate;
import java.util.List;

public interface MatchingService {
    /**
     * 根据时间偏好和信用分匹配学生
     * @param timePreference 时间偏好（白班/夜班/周末班）
     * @return 匹配的学生列表，按匹配度和信用分排序
     */
    List<User> matchStudentsByTimePreference(String timePreference);
    
    /**
     * 计算学生与时间偏好的匹配度
     * @param student 学生信息
     * @param timePreference 时间偏好
     * @return 匹配度分数（0-100）
     */
    int calculateMatchScore(User student, String timePreference);
    List<User> matchDailyStudents(int dayOfWeek, String timeSlot, LocalDate date);
}
