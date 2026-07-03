package com.xk.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AttendanceMapper {
    List<Map<String, Object>> getTodayAttendance(Integer studentId);
    List<Map<String, Object>> getHistoryAttendance(Integer studentId);
    Map<String, Object> getAttendanceByAssignmentId(Integer workAssignmentId);
    Map<String, Object> getWorkAssignmentById(Integer workAssignmentId);
    Map<String, Object> getJobById(Integer jobId);
    void insertAttendance(Integer workAssignmentId, Integer studentId, LocalDateTime checkInTime, int isLate, int lateMinutes);
    void updateCheckOutTime(Integer workAssignmentId, LocalDateTime checkOutTime);
    void updateWorkAssignmentStatus(Integer workAssignmentId, String status);

    // 新增方法
    void updateActualStartTime(Integer workAssignmentId, LocalDateTime actualStartTime);
    void updateMerchantSettled(Integer workAssignmentId, LocalDateTime settleTime);
    void updateWorkDuration(@Param("workAssignmentId") Integer workAssignmentId,
                            @Param("workDurationMinutes") Integer workDurationMinutes);
    
    // 商户相关方法
    void updateAttendanceStatus(Integer workAssignmentId, String status);
    void clearLateFields(Integer workAssignmentId);
    void insertAbsentRecord(Integer workAssignmentId, Integer studentId, String reason);

}