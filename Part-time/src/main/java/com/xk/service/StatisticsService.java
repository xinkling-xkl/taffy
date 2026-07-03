package com.xk.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface StatisticsService {

    // 商户
    List<Map<String, Object>> getRecruitmentProgress(int merchantId);
    List<Map<String, Object>> getAttendanceDistribution(int merchantId);
    List<Map<String, Object>> getDailyCheckinTrend(int merchantId);
    List<Map<String, Object>> getTopWorkers(int merchantId, int limit);

    // 学生
    List<Map<String, Object>> getCreditTrend(int studentId);
    List<Map<String, Object>> getWorkHours(int studentId);
    List<Map<String, Object>> getJobTypeDistribution(int studentId);

    // 管理员
    List<Map<String, Object>> getUserGrowth();
    List<Map<String, Object>> getIdentityDistribution();
    List<Map<String, Object>> getJobStatusStats();
    List<Map<String, Object>> getAttendanceOverall();
    List<Map<String, Object>> getMerchantCreditTrend(int merchantId);
    Map<String, Object> getMerchantEvaluationSummary(int merchantId, LocalDate weekStart, LocalDate weekEnd);
}