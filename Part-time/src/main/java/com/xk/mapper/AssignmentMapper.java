package com.xk.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AssignmentMapper {
    List<Map<String, Object>> getStudentAssignments(Integer studentId);
    List<Map<String, Object>> getMerchantAssignments(Integer merchantId);
    Map<String, Object> getAssignmentById(Integer assignmentId);
    Map<String, Object> getRequirementById(Integer requirementId);
    void updateAssignmentStatus(Integer assignmentId, String status);
    void insertAssignment(Integer studentId, Integer jobId, Integer merchantRequirementId, LocalDate workDate, String timeSlot, String status, LocalDateTime createdAt);
    void updateStudentSchedule(Integer studentId, Integer dayOfWeek, String timeSlot, Integer isAvailable);

    List<Map<String, Object>> getRequirementsByJobId(Integer jobId);

}