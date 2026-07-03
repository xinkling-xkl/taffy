package com.xk.service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

public interface AttendanceService {
    String checkIn(Integer workAssignmentId, Integer studentId, String checkInTime, Boolean isLate, Integer lateMinutes);
    String checkOut(Integer workAssignmentId, Integer studentId, String checkOutTime);
    List<Map<String, Object>> getTodayAssignments(Integer studentId);
    List<Map<String, Object>> getHistoryAttendances(Integer studentId);
    boolean checkMerchantConfirm(Integer workAssignmentId);
    String merchantConfirm(Integer workAssignmentId, Integer merchantId);
    
    // 新增商户相关方法
    List<Map<String, Object>> getMerchantTodayAssignments(Integer merchantId);
    List<Map<String, Object>> getMerchantHistoryAttendances(Integer merchantId);
    String markEarlyLeave(Integer workAssignmentId, Integer merchantId, String reason);
    String markLate(Integer workAssignmentId, Integer merchantId, String reason);
    String markAbsent(Integer workAssignmentId, Integer merchantId, String reason);
    String markCheckedIn(Integer workAssignmentId, Integer merchantId, String reason);
    String merchantCheckOut(Integer workAssignmentId, Integer merchantId, String checkOutTime);

    @Transactional
    /**
     * 恢复迟到为正常签到
     */
    String markNormalCheckedIn(Integer workAssignmentId, Integer merchantId);
}