package com.xk.controller;

import com.xk.common.Result;
import com.xk.mapper.MatchingHistoryMapper;
import com.xk.service.SystemConfigService;
import com.xk.service.impl.MatchingAiServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matching")
public class MatchingController {

    @Autowired
    private MatchingAiServiceImpl matchingAiServiceImpl;

    @Autowired
    private MatchingHistoryMapper matchingHistoryMapper;

    @Autowired
    private SystemConfigService systemConfigService;


    @PostMapping("/match-students")
    public Result<Map<String, Object>> matchStudents(@RequestBody Map<String, String> request) {
        String timePreference = request.get("timePreference");
        if (timePreference == null || timePreference.isEmpty()) {
            return Result.error("时间偏好不能为空");
        }
        try {
            Map<String, Integer> params = new com.fasterxml.jackson.databind.ObjectMapper().readValue(timePreference, Map.class);
            int dayOfWeek = params.get("dayOfWeek");
            String timeSlot = String.valueOf(params.get("timeSlot"));
            return Result.success(matchingAiServiceImpl.aiMatchStudentsBySlot(dayOfWeek, timeSlot));
        } catch (Exception e) {
            return Result.error("参数解析失败");
        }
    }

    @PostMapping("/calculate-score")
    public Result<Map<String, Object>> calculateScore(@RequestBody Map<String, Object> request) {
        Integer studentId = (Integer) request.get("studentId");
        String timePreference = (String) request.get("timePreference");
        if (studentId == null || timePreference == null) {
            return Result.error("参数缺失");
        }
        int score = matchingAiServiceImpl.aiCalculateMatchScore(studentId, timePreference);
        return Result.success(Map.of("score", score, "message", "AI匹配度计算完成"));
    }

    @PostMapping("/match-daily-students")
    public Result<Map<String, Object>> matchDailyStudents(@RequestBody Map<String, Object> request) {
        int dayOfWeek = (Integer) request.get("dayOfWeek");
        String timeSlot = (String) request.get("timeSlot");
        String dateStr = (String) request.get("date");
        LocalDate date = LocalDate.parse(dateStr);
        return Result.success(matchingAiServiceImpl.aiMatchDailyStudents(dayOfWeek, timeSlot, date));
    }

    @PostMapping("/ai-chat")
    public Result<Map<String, Object>> aiChat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String userIdStr = request.get("userId");
        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }
        Map<String, Object> result = matchingAiServiceImpl.processMatching(message, userIdStr);
        return Result.success(result);
    }

    /**
     * 空缺匹配：根据当前用户的身份，智能分析时间空缺并推荐填补
     */
    @PostMapping("/gap-match")
    public Result<Map<String, Object>> gapMatch(@RequestBody Map<String, String> request) {
        String userIdStr = request.get("userId");
        if (userIdStr == null) {
            return Result.error("userId 不能为空");
        }
        Map<String, Object> result = matchingAiServiceImpl.gapMatch(userIdStr);
        return Result.success(result);
    }

    /**
     * 执行用户确认的 Agent 操作（联系学生 / 申请兼职）
     */
    @PostMapping("/execute-action")
    public Result<Map<String, Object>> executeAction(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        Integer matchIndex = (Integer) request.get("matchIndex");
        String actionName = (String) request.get("actionName");

        if (sessionId == null || matchIndex == null || actionName == null) {
            return Result.error("参数缺失：sessionId、matchIndex、actionName 不能为空");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> extraParams = (Map<String, Object>) request.get("extraParams");

        Map<String, Object> result = matchingAiServiceImpl.executeAction(sessionId, matchIndex, actionName, extraParams);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Result.success((String) result.get("message"), result);
        }
        return Result.error((String) result.getOrDefault("message", "操作失败"));
    }

    @GetMapping("/history")
    public Result<Map<String, Object>> getHistory(@RequestParam int userId) {
        List<Map<String, Object>> history = matchingHistoryMapper.selectByUserId(userId, 10);
        return Result.success(Map.of("data", history));
    }

    @DeleteMapping("/history/{id}")
    public Result<?> deleteHistory(@PathVariable int id) {
        int rows = matchingHistoryMapper.deleteById(id);
        return rows > 0 ? Result.successMsg("删除成功") : Result.error("删除失败");
    }

    @DeleteMapping("/history")
    public Result<?> deleteAllHistory(@RequestParam int userId) {
        int rows = matchingHistoryMapper.deleteByUserId(userId);
        return Result.successMsg("已清除 " + rows + " 条历史");
    }

    @GetMapping("/ai-avatar")
    public Result<String> getAiAvatar() {
        String avatar = systemConfigService.getConfig("ai_avatar");
        return Result.success(avatar != null ? avatar : "");
    }

    @PostMapping("/admin/ai-avatar")
    public Result<?> setAiAvatar(@RequestBody Map<String, String> request) {
        String avatar = request.get("avatar");
        if (avatar == null || avatar.trim().isEmpty()) {
            return Result.error("头像URL不能为空");
        }
        systemConfigService.setConfig("ai_avatar", avatar);
        return Result.successMsg("AI头像设置成功");
    }

    @GetMapping(value = "/ai-chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiChatStream(@RequestParam String message, @RequestParam String userId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        matchingAiServiceImpl.processMatchingStream(message, userId, emitter);
        return emitter;
    }
}
