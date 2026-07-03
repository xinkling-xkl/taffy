package com.xk.service;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AssignmentService {
    List<Map<String, Object>> getStudentAssignments(Integer studentId);
    List<Map<String, Object>> getMerchantAssignments(Integer merchantId);
    String confirmAssignment(Integer assignmentId);
    String inviteStudent(Integer studentId, Integer jobId, Integer merchantRequirementId, String timeSlot, Integer dayOfWeek);
    void createWorkAssignment(Integer studentId, Integer jobId, Integer merchantRequirementId, LocalDate workDate, String timeSlot, String status);


}
