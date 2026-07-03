package com.xk.controller;

import com.xk.common.Result;
import com.xk.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/merchant/recruitment-progress")
    public Result<List<Map<String, Object>>> recruitmentProgress(@RequestParam int merchantId) {
        return Result.success(statisticsService.getRecruitmentProgress(merchantId));
    }

    @GetMapping("/merchant/attendance-distribution")
    public Result<List<Map<String, Object>>> attendanceDistribution(@RequestParam int merchantId) {
        return Result.success(statisticsService.getAttendanceDistribution(merchantId));
    }

    @GetMapping("/merchant/daily-checkin-trend")
    public Result<List<Map<String, Object>>> dailyCheckinTrend(@RequestParam int merchantId) {
        return Result.success(statisticsService.getDailyCheckinTrend(merchantId));
    }

    @GetMapping("/merchant/top-workers")
    public Result<List<Map<String, Object>>> topWorkers(@RequestParam int merchantId, @RequestParam(defaultValue = "5") int limit) {
        return Result.success(statisticsService.getTopWorkers(merchantId, limit));
    }

    @GetMapping("/student/credit-trend")
    public Result<List<Map<String, Object>>> creditTrend(@RequestParam int studentId) {
        return Result.success(statisticsService.getCreditTrend(studentId));
    }

    @GetMapping("/student/work-hours")
    public Result<List<Map<String, Object>>> workHours(@RequestParam int studentId) {
        return Result.success(statisticsService.getWorkHours(studentId));
    }

    @GetMapping("/student/job-type-distribution")
    public Result<List<Map<String, Object>>> jobTypeDistribution(@RequestParam int studentId) {
        return Result.success(statisticsService.getJobTypeDistribution(studentId));
    }

    @GetMapping("/admin/user-growth")
    public Result<List<Map<String, Object>>> userGrowth() {
        return Result.success(statisticsService.getUserGrowth());
    }

    @GetMapping("/admin/identity-distribution")
    public Result<List<Map<String, Object>>> identityDistribution() {
        return Result.success(statisticsService.getIdentityDistribution());
    }

    @GetMapping("/admin/job-status-stats")
    public Result<List<Map<String, Object>>> jobStatusStats() {
        return Result.success(statisticsService.getJobStatusStats());
    }

    @GetMapping("/admin/attendance-overall")
    public Result<List<Map<String, Object>>> attendanceOverall() {
        return Result.success(statisticsService.getAttendanceOverall());
    }

    @GetMapping("/merchant/credit-trend")
    public Result<List<Map<String, Object>>> merchantCreditTrend(@RequestParam int merchantId) {
        return Result.success(statisticsService.getMerchantCreditTrend(merchantId));
    }

    @GetMapping("/merchant/evaluation-summary")
    public Result<Map<String, Object>> merchantEvaluationSummary(@RequestParam int merchantId) {
        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(7);
        Map<String, Object> summary = statisticsService.getMerchantEvaluationSummary(merchantId, weekStart, weekEnd);
        int good = ((Number) summary.getOrDefault("good", 0)).intValue();
        int bad = ((Number) summary.getOrDefault("bad", 0)).intValue();
        return Result.success(Map.of("good", good, "bad", bad));
    }
}
