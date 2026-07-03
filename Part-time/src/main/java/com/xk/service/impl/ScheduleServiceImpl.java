package com.xk.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xk.entity.StudentSchedule;
import com.xk.entity.User;
import com.xk.mapper.ScheduleMapper;
import com.xk.mapper.UserMapper;
import com.xk.service.CacheClearService;
import com.xk.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    @Autowired
    private ScheduleMapper scheduleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MatchingAiServiceImpl matchingAiService;
    @Autowired
    private CacheClearService cacheClearService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StudentSchedule> getStudentSchedule(Integer studentId) {
        return scheduleMapper.getStudentSchedule(studentId);
    }

    @Override
    public void saveStudentSchedule(List<StudentSchedule> scheduleList) {
        if (scheduleList == null || scheduleList.isEmpty()) {
            return;
        }
        Integer studentId = scheduleList.get(0).getStudentId();
        for (StudentSchedule schedule : scheduleList) {
            scheduleMapper.deleteStudentSchedule(schedule.getStudentId(), schedule.getDayOfWeek(), schedule.getTimeSlot());
            scheduleMapper.insertStudentSchedule(schedule);
        }
        if (studentId != null) {
            syncTimePreferenceFromSchedule(studentId);
            cacheClearService.clearStudentCache(studentId);
        }
    }

    private void syncTimePreferenceFromSchedule(Integer studentId) {
        try {
            List<StudentSchedule> schedules = scheduleMapper.getStudentSchedule(studentId);
            List<Map<String, Object>> prefs = new ArrayList<>();
            for (StudentSchedule s : schedules) {
                if (s.getIsAvailable() != null && s.getIsAvailable() == 1) {
                    Map<String, Object> pref = new HashMap<>();
                    pref.put("day", s.getDayOfWeek());
                    int period = "morning".equals(s.getTimeSlot()) ? 1 :
                                 "afternoon".equals(s.getTimeSlot()) ? 2 : 3;
                    pref.put("period", period);
                    prefs.add(pref);
                }
            }
            String json = objectMapper.writeValueAsString(prefs);
            userMapper.updateTimePreference(studentId, json);
        } catch (Exception e) {
            System.err.println("同步timepreference失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getMerchantRequirements(Integer merchantId) {
        return scheduleMapper.getMerchantRequirements(merchantId);
    }
}