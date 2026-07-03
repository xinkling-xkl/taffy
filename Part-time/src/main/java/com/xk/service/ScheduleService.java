package com.xk.service;

import com.xk.entity.StudentSchedule;

import java.util.List;
import java.util.Map;

public interface ScheduleService {
    List<StudentSchedule> getStudentSchedule(Integer studentId);
    void saveStudentSchedule(List<StudentSchedule> scheduleList);
    List<Map<String, Object>> getMerchantRequirements(Integer merchantId);
}
