package com.xk.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xk.entity.User;
import com.xk.mapper.AssignmentMapper;
import com.xk.mapper.JobMapper;
import com.xk.mapper.UserMapper;
import com.xk.mapper.WorkAssignmentMapper;
import com.xk.service.CacheClearService;
import com.xk.service.UserService;
import org.hibernate.validator.internal.util.stereotypes.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CacheClearServiceImpl implements CacheClearService {

    private final Map<Integer, Set<String>> studentAvailableCache = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> jobAvailableCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JobMapper jobMapper;

    @Autowired
    private WorkAssignmentMapper workAssignmentMapper;

    @Autowired
    private AssignmentMapper assignmentMapper;

    @Override
    public void clearStudentCache(int studentId) {
        studentAvailableCache.remove(studentId);
    }

    @Override
    public void clearJobCache(int jobId) {
        jobAvailableCache.remove(jobId);
    }

    @Override
    public Set<String> getStudentAvailableSlots(int studentId) {
        return studentAvailableCache.computeIfAbsent(studentId, id -> {
            User student = userMapper.getUserById(id);
            if (student == null || student.getTimepreference() == null || student.getTimepreference().trim().isEmpty()) {
                System.out.println("学生 " + id + " 无时间偏好或用户不存在");
                return Collections.emptySet();
            }

            Set<String> available = new HashSet<>();
            try {
                List<Map<String, Object>> prefs = objectMapper.readValue(
                        student.getTimepreference(),
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> p : prefs) {
                    int day = ((Number) p.get("day")).intValue();
                    int period = ((Number) p.get("period")).intValue();
                    String slot = period == 1 ? "morning" : period == 2 ? "afternoon" : "evening";
                    available.add(day + "_" + slot);
                }
                System.out.println("学生 " + id + " 解析偏好时段: " + available);
            } catch (Exception e) {
                System.err.println("解析学生时间偏好失败: " + e.getMessage());
                return Collections.emptySet();
            }

            // 排除未来占用（work_date >= today）
            try {
                LocalDate today = LocalDate.now();
                List<Map<String, Object>> occupied = workAssignmentMapper.getOccupiedSlotsForStudent(id, today);
                System.out.println("学生 " + id + " 未来占用记录数: " + occupied.size());
                for (Map<String, Object> occ : occupied) {
                    LocalDate workDate = ((java.sql.Date) occ.get("work_date")).toLocalDate();
                    int day = workDate.getDayOfWeek().getValue();
                    String slot = (String) occ.get("time_slot");
                    available.remove(day + "_" + slot);
                    System.out.println("  移除占用: " + day + "_" + slot);
                }
            } catch (Exception e) {
                System.err.println("获取学生占用时段失败，跳过排除: " + e.getMessage());
            }

            // 过滤过期时段（本周已过日期 + 今天已过起始时间）
            LocalDateTime now = LocalDateTime.now();
            LocalDate todayDate = now.toLocalDate();
            int currentDayOfWeek = todayDate.getDayOfWeek().getValue();
            int currentHour = now.getHour();
            int currentMinute = now.getMinute();
            Map<String, Integer> slotStartHour = Map.of("morning", 9, "afternoon", 14, "evening", 18);
            Map<String, Integer> slotStartMinute = Map.of("morning", 0, "afternoon", 0, "evening", 0);
            Set<String> finalAvailable = new HashSet<>();
            for (String key : available) {
                String[] parts = key.split("_");
                int day = Integer.parseInt(parts[0]);
                String slot = parts[1];
                if (day < currentDayOfWeek) {
                    System.out.println("  过滤过期星期: " + key + " (当前星期=" + currentDayOfWeek + ")");
                    continue;
                }
                if (day == currentDayOfWeek) {
                    Integer startHour = slotStartHour.get(slot);
                    Integer startMin = slotStartMinute.get(slot);
                    if (startHour != null && (currentHour > startHour || (currentHour == startHour && currentMinute >= startMin))) {
                        System.out.println("  过滤今天已开始时段: " + key + " (当前时间=" + currentHour + ":" + String.format("%02d", currentMinute) + ", 起始时间=" + startHour + ":" + String.format("%02d", startMin) + ")");
                        continue;
                    }
                }
                finalAvailable.add(key);
            }
            System.out.println("学生 " + id + " 最终空闲时段: " + finalAvailable);
            return finalAvailable;
        });
    }

    @Override
    public Set<String> getJobAvailableSlots(int jobId) {
        return jobAvailableCache.computeIfAbsent(jobId, id -> {
            Set<String> available = new HashSet<>();
            List<Map<String, Object>> requirements = assignmentMapper.getRequirementsByJobId(jobId);
            if (requirements == null) return available;
            for (Map<String, Object> req : requirements) {
                try {
                    int day = Integer.parseInt(req.get("day_of_week").toString());
                    String slot = (String) req.get("time_slot");
                    int needed = Integer.parseInt(req.get("needed_count").toString());
                    int filled = Integer.parseInt(req.get("filled_count").toString());
                    if (filled < needed) {
                        available.add(day + "_" + slot);
                    }
                } catch (Exception e) {
                    System.err.println("解析需求记录失败: " + e.getMessage());
                }
            }

            LocalDateTime now = LocalDateTime.now();
            int currentDayOfWeek = now.toLocalDate().getDayOfWeek().getValue();
            int currentHour = now.getHour();
            int currentMinute = now.getMinute();
            Map<String, Integer> slotStartHour = Map.of("morning", 9, "afternoon", 14, "evening", 18);
            Map<String, Integer> slotStartMinute = Map.of("morning", 0, "afternoon", 0, "evening", 0);
            Set<String> finalAvailable = new HashSet<>();
            for (String key : available) {
                String[] parts = key.split("_");
                int day = Integer.parseInt(parts[0]);
                String slot = parts[1];
                if (day < currentDayOfWeek) {
                    continue;
                }
                if (day == currentDayOfWeek) {
                    Integer startHour = slotStartHour.get(slot);
                    Integer startMin = slotStartMinute.get(slot);
                    if (startHour != null && (currentHour > startHour || (currentHour == startHour && currentMinute >= startMin))) {
                        continue;
                    }
                }
                finalAvailable.add(key);
            }
            System.out.println("兼职 " + jobId + " 最终空缺时段: " + finalAvailable);
            return finalAvailable;
        });
    }
}