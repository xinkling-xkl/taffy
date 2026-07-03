package com.xk.service.impl;

import com.xk.mapper.StatisticsMapper;
import com.xk.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private StatisticsMapper statisticsMapper;

    @Override
    public List<Map<String, Object>> getRecruitmentProgress(int merchantId) {
        return statisticsMapper.getRecruitmentProgress(merchantId);
    }

    @Override
    public List<Map<String, Object>> getAttendanceDistribution(int merchantId) {
        return statisticsMapper.getAttendanceDistribution(merchantId);
    }

    @Override
    public List<Map<String, Object>> getDailyCheckinTrend(int merchantId) {
        return statisticsMapper.getDailyCheckinTrend(merchantId);
    }

    @Override
    public List<Map<String, Object>> getTopWorkers(int merchantId, int limit) {
        return statisticsMapper.getTopWorkers(merchantId, limit);
    }

    @Override
    public List<Map<String, Object>> getCreditTrend(int studentId) {
        return statisticsMapper.getCreditTrend(studentId);
    }

    @Override
    public List<Map<String, Object>> getWorkHours(int studentId) {
        return statisticsMapper.getWorkHours(studentId);
    }

    @Override
    public List<Map<String, Object>> getJobTypeDistribution(int studentId) {
        return statisticsMapper.getJobTypeDistribution(studentId);
    }

    @Override
    public List<Map<String, Object>> getUserGrowth() {
        return statisticsMapper.getUserGrowth();
    }

    @Override
    public List<Map<String, Object>> getIdentityDistribution() {
        return statisticsMapper.getIdentityDistribution();
    }

    @Override
    public List<Map<String, Object>> getJobStatusStats() {
        return statisticsMapper.getJobStatusStats();
    }

    @Override
    public List<Map<String, Object>> getAttendanceOverall() {
        return statisticsMapper.getAttendanceOverall();
    }

    @Override
    public List<Map<String, Object>> getMerchantCreditTrend(int merchantId) {
        return statisticsMapper.getMerchantCreditTrend(merchantId);
    }

    @Override
    public Map<String, Object> getMerchantEvaluationSummary(int merchantId, LocalDate weekStart, LocalDate weekEnd) {
        return statisticsMapper.getMerchantEvaluationSummary(merchantId, weekStart, weekEnd);
    }
}