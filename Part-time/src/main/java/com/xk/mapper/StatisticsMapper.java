package com.xk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface StatisticsMapper {

    // ========== 商户 ==========
    List<Map<String, Object>> getRecruitmentProgress(@Param("merchantId") int merchantId);

    List<Map<String, Object>> getAttendanceDistribution(@Param("merchantId") int merchantId);

    List<Map<String, Object>> getDailyCheckinTrend(@Param("merchantId") int merchantId);

    List<Map<String, Object>> getTopWorkers(@Param("merchantId") int merchantId,
                                            @Param("limit") int limit);

    // ========== 学生 ==========
    List<Map<String, Object>> getCreditTrend(@Param("studentId") int studentId);

    List<Map<String, Object>> getWorkHours(@Param("studentId") int studentId);

    List<Map<String, Object>> getJobTypeDistribution(@Param("studentId") int studentId);

    // ========== 管理员 ==========
    List<Map<String, Object>> getUserGrowth();

    List<Map<String, Object>> getIdentityDistribution();

    List<Map<String, Object>> getJobStatusStats();

    List<Map<String, Object>> getAttendanceOverall();
    // 商户信用趋势（近30天）
    List<Map<String, Object>> getMerchantCreditTrend(@Param("merchantId") int merchantId);

    // 商户本周评价摘要
    Map<String, Object> getMerchantEvaluationSummary(@Param("merchantId") int merchantId,
                                                     @Param("weekStart") LocalDate weekStart,
                                                     @Param("weekEnd") LocalDate weekEnd);
}