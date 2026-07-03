package com.xk.service.impl;

import com.xk.entity.User;
import com.xk.mapper.AssignmentMapper;
import com.xk.mapper.ScheduleMapper;
import com.xk.mapper.UserMapper;
import com.xk.mapper.WorkAssignmentMapper;
import com.xk.service.MatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MatchingServiceImpl implements MatchingService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ScheduleMapper scheduleMapper;
    @Autowired
    private WorkAssignmentMapper workAssignmentMapper;

    @Override
    public List<User> matchStudentsByTimePreference(String timePreference) {
        // 新接口：传入[dayOfWeek, timeSlot] 映射
        try {
            Map<String, Integer> params = new com.fasterxml.jackson.databind.ObjectMapper().readValue(timePreference, Map.class);
            int dayOfWeek = params.get("dayOfWeek");
            String timeSlot = String.valueOf(params.get("timeSlot"));
            return matchStudents(dayOfWeek, timeSlot);
        } catch (Exception e) {
            return List.of();
        }
    }

    // 根据具体时段查询空闲学生
    public List<User> matchStudents(int dayOfWeek, String timeSlot) {
        // 从 student_schedule 表获取该时段空闲的学生ID
        List<Integer> studentIds = scheduleMapper.findAvailableStudentIds(dayOfWeek, timeSlot);
        // 进一步过滤：未处于处罚状态、信用分>=50
        return studentIds.stream()
                .map(userMapper::getUserById)
                .filter(u -> u != null && "学生".equals(u.getIdentity())
                        && (u.getIsPenalty() == null || u.getIsPenalty() == 0)
                        && u.getCredit() != null && u.getCredit() >= 50)
                .collect(Collectors.toList());
    }

    @Override
    public int calculateMatchScore(User student, String timePreference) {
        // 此方法暂不用于新邀请流程，保留旧调用
        return 60; // 返回默认值
    }

    /**
     * 匹配日结学生（排除当天该时段已有工作分配的学生）
     * @param dayOfWeek 星期几（1-7）
     * @param timeSlot   morning/afternoon/evening
     * @param date      具体日期，用于排除已分配学生
     * @return 匹配的学生列表
     */
    public List<User> matchDailyStudents(int dayOfWeek, String timeSlot, LocalDate date) {
        // 1. 获取该时段常规空闲的学生ID
        List<Integer> availableIds = scheduleMapper.findAvailableStudentIds(dayOfWeek, timeSlot);
        if (availableIds.isEmpty()) return List.of();

        // 2. 获取当天该时段已分配的学生ID
        List<Integer> assignedToday = workAssignmentMapper.getStudentIdsByDateAndSlot(date, timeSlot);

        // 3. 排除已分配的学生
        Set<Integer> assignedSet = new HashSet<>(assignedToday);
        List<Integer> finalIds = availableIds.stream()
                .filter(id -> !assignedSet.contains(id))
                .collect(Collectors.toList());

        // 4. 获取用户对象，过滤信用分和处罚状态
        return finalIds.stream()
                .map(userMapper::getUserById)
                .filter(u -> u != null && "学生".equals(u.getIdentity())
                        && (u.getIsPenalty() == null || u.getIsPenalty() == 0)
                        && u.getCredit() != null && u.getCredit() >= 50)
                .collect(Collectors.toList());
    }
}