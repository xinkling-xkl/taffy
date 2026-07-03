package com.xk.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xk.entity.Job;
import com.xk.entity.Message;
import com.xk.entity.User;
import com.xk.mapper.*;
import com.xk.service.CacheClearService;
import com.xk.service.JobService;
import com.xk.service.MessageService;
import com.xk.service.UserService;
import com.xk.util.TimeSlotExtractor;
import org.hibernate.validator.internal.util.stereotypes.Lazy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class MatchingAiServiceImpl {


    @Autowired
    private ChatClient chatClient;
    @Autowired
    @Lazy
    private UserService userService;
    @Autowired
    private CacheClearService cacheClearService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private JobMapper jobMapper;
    @Autowired
    private JobApplicationMapper jobApplicationMapper;
    @Autowired
    private ScheduleMapper scheduleMapper;
    @Autowired
    private WorkAssignmentMapper workAssignmentMapper;
    @Autowired
    private AssignmentMapper assignmentMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OllamaChatModel ollamaChatModel;
    @Autowired
    private MatchingHistoryMapper matchingHistoryMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SessionContext> sessionStore = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> studentAvailableCache = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> jobAvailableCache = new ConcurrentHashMap<>();



    public Map<String, Object> processMatching(String userInput, String userIdStr) {
        if (!isMatchingIntent(userInput)) {
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", UUID.randomUUID().toString());
            result.put("matches", Collections.emptyList()); result.put("plans", Collections.emptyList());
            result.put("total", 0); result.put("thinking", "");
            result.put("content", "暂时没有该服务哦，或者换个问题试试。");
            return result;
        }
        if (userInput.toLowerCase().contains("缺口") || userInput.toLowerCase().contains("空缺")
                || userInput.toLowerCase().contains("招满") || userInput.toLowerCase().contains("填补")) {
            return gapMatch(userIdStr);
        }
        User currentUser = userService.getUserById(Integer.parseInt(userIdStr));
        String identity = currentUser != null ? currentUser.getIdentity() : "";

        List<TimeSlotExtractor.TimeSlot> timeSlots = TimeSlotExtractor.extract(userInput);
        Set<String> requiredSlotSet = timeSlotsToSet(timeSlots);
        String keywordHint = extractKeywordHints(userInput);
        String timeHint = "";
        boolean preferDaily = userInput.contains("日结");
        boolean preferHighSalary = userInput.contains("高薪") || userInput.contains("高薪资");
        boolean preferCampus = userInput.contains("校园") || userInput.contains("学校附近") || userInput.contains("校内");

        String prompt;
        Map<Integer, Integer> ragScoreLookup = new HashMap<>();
        if ("商户".equals(identity)) {
            List<User> rawStudents = timeSlots.isEmpty() ? getAvailableStudents() : filterStudentsBySlots(timeSlots);
            List<User> students = filterStudentsByRealAvailability(rawStudents, timeSlots);
            Map<User, Integer> ragScores = ragPreScoreStudents(students, requiredSlotSet);
            ragScores.forEach((user, score) -> ragScoreLookup.put(user.getId(), score));
            List<Map.Entry<User, Integer>> sortedByRag = ragScores.entrySet().stream()
                .sorted(Map.Entry.<User, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
            List<User> topCandidates = new ArrayList<>();
            for (int i = 0; i < Math.min(10, sortedByRag.size()); i++) {
                topCandidates.add(sortedByRag.get(i).getKey());
            }
            timeHint = timeSlots.isEmpty() ? "" : "已提取时间约束: " + formatTimeSlots(timeSlots);
            if (!keywordHint.isEmpty()) timeHint = timeHint.isEmpty() ? keywordHint : timeHint + " | " + keywordHint;
            prompt = buildRagAgentPrompt(userInput, topCandidates, sortedByRag, requiredSlotSet, "student", identity, timeHint, currentUser);
        } else if ("学生".equals(identity)) {
            Set<String> realFreeSlots = cacheClearService.getStudentAvailableSlots(currentUser.getId());
            if (realFreeSlots.isEmpty()) {
                Map<String, Object> emptyResult = new HashMap<>();
                emptyResult.put("sessionId", UUID.randomUUID().toString());
                emptyResult.put("matches", Collections.emptyList()); emptyResult.put("plans", Collections.emptyList());
                emptyResult.put("total", 0);
                emptyResult.put("thinking", "您当前没有空闲时段（所有偏好时段均已被工作占用），暂无可匹配的兼职。");
                return emptyResult;
            }
            Set<Integer> jobIds = new LinkedHashSet<>();
            for (String slotKey : realFreeSlots) {
                String[] parts = slotKey.split("_");
                List<Job> jobsBySlot = jobMapper.selectByDayAndSlot(Integer.parseInt(parts[0]), parts[1]);
                for (Job j : jobsBySlot) jobIds.add(j.getId());
            }
            List<Job> matchedJobs = new ArrayList<>();
            for (Integer jid : jobIds) { Job j = jobMapper.selectById(jid); if (j != null && "进行中".equals(j.getStatus()) && !"已招满".equals(j.getStatus())) matchedJobs.add(j); }
            List<Job> jobs = matchedJobs.stream().filter(j -> !cacheClearService.getJobAvailableSlots(j.getId()).isEmpty()).collect(Collectors.toList());
            if (preferDaily) jobs = jobs.stream().filter(j -> "daily".equals(j.getRemunerationType())).collect(Collectors.toList());
            if (preferHighSalary) jobs.sort((a, b) -> { try { return Integer.compare(parseSalary(b.getSalary()), parseSalary(a.getSalary())); } catch (Exception e) { return 0; } });
            if (preferCampus) jobs = jobs.stream().filter(j -> j.getAddress() != null && (j.getAddress().contains("校") || j.getAddress().contains("校内"))).collect(Collectors.toList());
            Map<Job, Integer> ragScores = ragPreScoreJobs(jobs, realFreeSlots);
            ragScores.forEach((job, score) -> ragScoreLookup.put(job.getId(), score));
            List<Map.Entry<Job, Integer>> sortedByRag = ragScores.entrySet().stream()
                .sorted(Map.Entry.<Job, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
            List<Job> topCandidates = new ArrayList<>();
            for (int i = 0; i < Math.min(10, sortedByRag.size()); i++) {
                topCandidates.add(sortedByRag.get(i).getKey());
            }
            timeHint = "基于您的真实空闲时段（已排除已占用时段）自动匹配";
            prompt = buildRagAgentPrompt(userInput, topCandidates, sortedByRag, realFreeSlots, "job", identity, timeHint, currentUser);
        } else {
            List<User> rawStudents = timeSlots.isEmpty() ? getAvailableStudents() : filterStudentsBySlots(timeSlots);
            List<User> students = filterStudentsByRealAvailability(rawStudents, timeSlots);
            List<Job> rawJobs = timeSlots.isEmpty() ? getAvailableJobs() : filterJobsBySlots(timeSlots);
            Map<User, Integer> studentScores = ragPreScoreStudents(students, requiredSlotSet);
            studentScores.forEach((user, score) -> ragScoreLookup.put(user.getId(), score));
            List<Map.Entry<User, Integer>> sortedStudents = studentScores.entrySet().stream()
                .sorted(Map.Entry.<User, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
            List<User> topStudents = new ArrayList<>();
            for (int i = 0; i < Math.min(5, sortedStudents.size()); i++) topStudents.add(sortedStudents.get(i).getKey());
            Map<Job, Integer> jobScores = ragPreScoreJobs(rawJobs, requiredSlotSet);
            jobScores.forEach((job, score) -> ragScoreLookup.put(job.getId(), score));
            List<Map.Entry<Job, Integer>> sortedJobs = jobScores.entrySet().stream()
                .sorted(Map.Entry.<Job, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
            List<Job> topJobs = new ArrayList<>();
            for (int i = 0; i < Math.min(5, sortedJobs.size()); i++) topJobs.add(sortedJobs.get(i).getKey());
            timeHint = timeSlots.isEmpty() ? "" : "已提取时间约束: " + formatTimeSlots(timeSlots);
            if (!keywordHint.isEmpty()) timeHint = timeHint.isEmpty() ? keywordHint : timeHint + " | " + keywordHint;
            prompt = buildRagAgentPrompt(userInput, topStudents, sortedStudents, requiredSlotSet, "student", identity, timeHint, currentUser);
            if (!topJobs.isEmpty()) {
                String jobPrompt = buildRagAgentPrompt(userInput, topJobs, sortedJobs, requiredSlotSet, "job", identity, timeHint, currentUser);
                prompt = prompt + "\n---\n同时也考虑以下兼职：\n" + jobPrompt.substring(jobPrompt.indexOf("### 候选列表"));
            }
        }

        String aiResponse = callAi(prompt, 256);

        boolean aiSuccess = false; String thinking = ""; List<Map<String, Object>> matches = new ArrayList<>();

        if (aiResponse != null && !aiResponse.trim().isEmpty()) {
            thinking = extractThinkingFromResponse(aiResponse);
            String cleaned = removeThinkTags(aiResponse); cleaned = cleanAiResponse(cleaned);
            List<AIMatchResult> matchResults = parseSimpleMatchesResponse(cleaned);
            if (matchResults != null && !matchResults.isEmpty()) {
                for (AIMatchResult r : matchResults) { Map<String, Object> item = buildMatchItem(r); if (item != null) { item.putIfAbsent("suggestedActions", defaultActions("student".equals(r.getType()) ? "student" : "job")); matches.add(item); } }
                sanitizeMatchItems(matches, identity, currentUser, requiredSlotSet);
                for (Map<String, Object> match : matches) {
                    Integer matchId = (Integer) match.get("id");
                    Integer ragScore = ragScoreLookup.get(matchId);
                    if (ragScore != null) {
                        int aiScore = (Integer) match.get("score");
                        match.put("score", Math.max(aiScore, ragScore));
                    }
                }
                matches.sort((a, b) -> Integer.compare((Integer)b.get("score") + (Integer)b.getOrDefault("credit",0)/10, (Integer)a.get("score") + (Integer)a.getOrDefault("credit",0)/10));
                if (matches.size() > 5) matches = matches.subList(0, 5);
                aiSuccess = true;
            }
        }
        if (!aiSuccess) { thinking = "AI 解析未成功，已使用本地匹配算法。";
            if ("商户".equals(identity)) {
                List<User> allStudents = timeSlots.isEmpty() ? getAvailableStudents() : filterStudentsBySlots(timeSlots);
                matches = buildLocalStudentMatches(filterStudentsByRealAvailability(allStudents, timeSlots), requiredSlotSet);
            } else if ("学生".equals(identity)) {
                Set<String> sf = cacheClearService.getStudentAvailableSlots(currentUser.getId());
                Set<Integer> jids = new LinkedHashSet<>();
                for (String k : sf) { String[] p = k.split("_"); for (Job j : jobMapper.selectByDayAndSlot(Integer.parseInt(p[0]), p[1])) jids.add(j.getId()); }
                List<Job> fj = jids.stream().map(jobMapper::selectById).filter(j -> j != null && "进行中".equals(j.getStatus())).collect(Collectors.toList());
                matches = buildLocalJobMatches(fj, sf);
            }
            matches.sort((a,b)->{int sa=(Integer)a.get("score"),ca=(Integer)a.getOrDefault("credit",0),sb=(Integer)b.get("score"),cb=(Integer)b.getOrDefault("credit",0); return Integer.compare(sb+cb/10, sa+ca/10);});
            if (matches.size() > 5) matches = matches.subList(0, 5);
        }

        String sessionId = UUID.randomUUID().toString();
        sessionStore.put(sessionId, new SessionContext(Integer.parseInt(userIdStr), identity, userInput, matches));
        try { matchingHistoryMapper.insert(Integer.parseInt(userIdStr), userInput, objectMapper.writeValueAsString(matches), matches.size()); } catch (Exception e) {}

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId); result.put("matches", matches); result.put("total", matches.size()); result.put("thinking", thinking);
        return result;
    }

    public Map<String, Object> aiMatchStudentsBySlot(int dayOfWeek, String timeSlot) {
        List<User> candidates = new ArrayList<>();
        List<Integer> ids = scheduleMapper.findAvailableStudentIds(dayOfWeek, timeSlot);
        for (Integer sid : ids) {
            User u = userService.getUserById(sid);
            if (u != null && "学生".equals(u.getIdentity())
                    && (u.getIsPenalty() == null || u.getIsPenalty() == 0)
                    && u.getCredit() != null && u.getCredit() >= 50
                    && u.getTimepreference() != null && !u.getTimepreference().trim().isEmpty()) {
                candidates.add(u);
            }
        }
        if (candidates.isEmpty()) return buildEmptyMatchResult();

        Set<String> requiredSlots = new HashSet<>();
        requiredSlots.add(dayOfWeek + "_" + timeSlot);
        Map<User, Integer> ragScores = ragPreScoreStudents(candidates, requiredSlots);
        List<Map.Entry<User, Integer>> sortedByRag = ragScores.entrySet().stream()
            .sorted(Map.Entry.<User, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());
        List<User> topCandidates = new ArrayList<>();
        for (int i = 0; i < Math.min(10, sortedByRag.size()); i++) topCandidates.add(sortedByRag.get(i).getKey());

        String prompt = buildRagAgentPrompt("商户按时段匹配学生: " + getDayName(dayOfWeek) + " " + periodToChinese(timeSlotToPeriod(timeSlot)), topCandidates, sortedByRag, requiredSlots, "student", "商户", "", null);
        String aiResponse = callAiWithSpring(prompt, 256);
        Map<Integer, Integer> ragLookup = new HashMap<>();
        ragScores.forEach((user, score) -> ragLookup.put(user.getId(), score));
        return parseAiMatches(aiResponse, topCandidates, "student", "商户", ragLookup);
    }

    public Map<String, Object> aiMatchDailyStudents(int dayOfWeek, String timeSlot, LocalDate date) {
        List<Integer> availableIds = scheduleMapper.findAvailableStudentIds(dayOfWeek, timeSlot);
        List<Integer> assignedToday = workAssignmentMapper.getStudentIdsByDateAndSlot(date, timeSlot);
        Set<Integer> assignedSet = new HashSet<>(assignedToday);
        List<User> candidates = new ArrayList<>();
        for (Integer sid : availableIds) {
            if (assignedSet.contains(sid)) continue;
            User u = userService.getUserById(sid);
            if (u != null && "学生".equals(u.getIdentity())
                    && (u.getIsPenalty() == null || u.getIsPenalty() == 0)
                    && u.getCredit() != null && u.getCredit() >= 50
                    && u.getTimepreference() != null && !u.getTimepreference().trim().isEmpty()) {
                candidates.add(u);
            }
        }
        if (candidates.isEmpty()) return buildEmptyMatchResult();

        Set<String> requiredSlots = new HashSet<>();
        requiredSlots.add(dayOfWeek + "_" + timeSlot);
        Map<User, Integer> ragScores = ragPreScoreStudents(candidates, requiredSlots);
        List<Map.Entry<User, Integer>> sortedByRag = ragScores.entrySet().stream()
            .sorted(Map.Entry.<User, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());
        List<User> topCandidates = new ArrayList<>();
        for (int i = 0; i < Math.min(10, sortedByRag.size()); i++) topCandidates.add(sortedByRag.get(i).getKey());

        String prompt = buildRagAgentPrompt("商户匹配日结学生: " + getDayName(dayOfWeek) + " " + periodToChinese(timeSlotToPeriod(timeSlot)) + " 日期:" + date, topCandidates, sortedByRag, requiredSlots, "student", "商户", "", null);
        String aiResponse2 = callAiWithSpring(prompt, 256);
        Map<Integer, Integer> ragLookup2 = new HashMap<>();
        ragScores.forEach((user, score) -> ragLookup2.put(user.getId(), score));
        return parseAiMatches(aiResponse2, topCandidates, "student", "商户", ragLookup2);
    }

    public int aiCalculateMatchScore(int studentId, String timePreferenceStr) {
        try {
            User student = userService.getUserById(studentId);
            if (student == null) return 0;

            Map<String, Integer> params = new com.fasterxml.jackson.databind.ObjectMapper().readValue(timePreferenceStr, Map.class);
            int dayOfWeek = params.get("dayOfWeek");
            String timeSlot = String.valueOf(params.get("timeSlot"));

            Set<String> studentFree = cacheClearService.getStudentAvailableSlots(studentId);
            String key = dayOfWeek + "_" + timeSlot;
            if (studentFree.contains(key)) return Math.min(100, 70 + (student.getCredit() != null ? student.getCredit() / 10 : 6));
            int anyOverlap = 0;
            for (String f : studentFree) { String[] p = f.split("_"); if (Integer.parseInt(p[0]) == dayOfWeek) anyOverlap = 1; }
            if (anyOverlap > 0) return Math.min(100, 40 + (student.getCredit() != null ? student.getCredit() / 10 : 6));
            return Math.min(100, Math.max(10, (student.getCredit() != null ? student.getCredit() : 60) / 2));
        } catch (Exception e) {
            return 60;
        }
    }

    private Map<String, Object> parseAiMatches(String aiResponse, List<?> candidates, String matchType, String identity, Map<Integer, Integer> ragScoreLookup) {
        List<Map<String, Object>> matches = new ArrayList<>();
        boolean aiSuccess = false;
        if (aiResponse != null && !aiResponse.trim().isEmpty()) {
            List<AIMatchResult> matchResults = parseSimpleMatchesResponse(aiResponse);
            if (matchResults != null && !matchResults.isEmpty()) {
                for (AIMatchResult r : matchResults) {
                    Map<String, Object> item = buildMatchItem(r);
                    if (item != null) { item.put("suggestedActions", defaultActions(matchType)); matches.add(item); }
                }
                sanitizeMatchItems(matches, identity);
                if (ragScoreLookup != null) {
                    for (Map<String, Object> m : matches) {
                        Integer rs = ragScoreLookup.get((Integer) m.get("id"));
                        if (rs != null) m.put("score", Math.max((Integer) m.get("score"), rs));
                    }
                }
                matches.sort((a, b) -> Integer.compare((Integer)b.get("score") + (Integer)b.getOrDefault("credit",0)/10, (Integer)a.get("score") + (Integer)a.getOrDefault("credit",0)/10));
                if (matches.size() > 5) matches = matches.subList(0, 5);
                aiSuccess = true;
            }
        }
        if (!aiSuccess && candidates != null && matchType.equals("student")) {
            @SuppressWarnings("unchecked")
            List<User> users = (List<User>) candidates;
            Set<String> slots = new HashSet<>();
            matches = buildLocalStudentMatches(users, slots);
            matches.sort((a,b)->{int sa=(Integer)a.get("score"),ca=(Integer)a.getOrDefault("credit",0),sb=(Integer)b.get("score"),cb=(Integer)b.getOrDefault("credit",0); return Integer.compare(sb+cb/10, sa+ca/10);});
            if (matches.size() > 5) matches = matches.subList(0, 5);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("matches", matches);
        result.put("total", matches.size());
        result.put("thinking", aiSuccess ? "AI智能匹配完成" : "使用本地匹配算法");
        return result;
    }

    private Map<String, Object> buildEmptyMatchResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("matches", Collections.emptyList());
        result.put("total", 0);
        result.put("thinking", "未找到符合条件的学生");
        return result;
    }

    public Map<String, Object> gapMatch(String userIdStr) {
        User currentUser = userService.getUserById(Integer.parseInt(userIdStr));
        if (currentUser == null) return buildGapResult(UUID.randomUUID().toString(), 0, null, "用户不存在");
        String identity = currentUser.getIdentity();
        if ("商户".equals(identity) || "管理员".equals(identity)) return buildMerchantGapResult(currentUser);
        else if ("学生".equals(identity)) return buildStudentGapResult(currentUser);
        else return buildGapResult(UUID.randomUUID().toString(), 0, null, "未知身份，暂不支持空缺匹配");
    }

    public Map<String, Object> getSessionContext(String sessionId) {
        SessionContext ctx = sessionStore.get(sessionId);
        if (ctx == null) return null;
        Map<String, Object> result = new HashMap<>();
        result.put("userId", ctx.userId); result.put("identity", ctx.identity); result.put("query", ctx.query); result.put("matches", ctx.matches);
        return result;
    }

    public Map<String, Object> executeAction(String sessionId, int matchIndex, String actionName) { return executeAction(sessionId, matchIndex, actionName, null); }

    public Map<String, Object> executeAction(String sessionId, int matchIndex, String actionName, Map<String, Object> extraParams) {
        SessionContext ctx = sessionStore.get(sessionId);
        if (ctx == null) return Map.of("success", false, "message", "会话已过期，请重新搜索");
        if (matchIndex < 0 || matchIndex >= ctx.matches.size()) return Map.of("success", false, "message", "无效的匹配项索引");
        Map<String, Object> match = ctx.matches.get(matchIndex);
        String type = (String) match.get("type");
        Map<String, Object> result = new HashMap<>(); result.put("action", actionName); result.put("type", type);
        try {
            switch (actionName) {
                case "contact_student": if (!"student".equals(type)) return Map.of("success", false, "message", "操作类型不匹配"); return executeContactStudent(ctx, match, extraParams);
                case "apply_job": if (!"job".equals(type)) return Map.of("success", false, "message", "操作类型不匹配"); return executeApplyJob(ctx, match);
                case "view_detail": result.put("success", true); result.put("message", "请查看详情"); result.put("url", match.get("url")); return result;
                default: return Map.of("success", false, "message", "不支持的操作: " + actionName);
            }
        } catch (Exception e) { e.printStackTrace(); return Map.of("success", false, "message", "操作执行失败: " + e.getMessage()); }
    }



    private Map<String, Object> executeContactStudent(SessionContext ctx, Map<String, Object> match, Map<String, Object> extraParams) {
        Object idObj = match.get("id");
        Integer studentId = idObj instanceof Number ? ((Number) idObj).intValue() : null;
        String studentName = (String) match.get("name");
        if (studentId == null) return Map.of("success", false, "message", "学生信息异常，无法发送消息");
        User merchant = userService.getUserById(ctx.userId);
        String merchantName = merchant != null ? merchant.getName() : "商户";
        Integer jobId = null; String jobTitle = null;
        if (extraParams != null) { Object jidObj = extraParams.get("jobId"); jobId = jidObj instanceof Number ? ((Number) jidObj).intValue() : null; jobTitle = (String) extraParams.get("jobTitle"); }

        StringBuilder content = new StringBuilder();
        content.append(String.format("您好%s，商户%s通过智能匹配找到了您，认为您的空闲时间很合适。\n\n", studentName, merchantName));
        if (jobId != null && jobTitle != null) content.append(String.format("推荐兼职：【%s】\n\n", jobTitle));
        content.append("如需沟通，请回复消息与商户沟通详情。");

        Message message = new Message(); message.setSenderId(ctx.userId); message.setReceiverId(studentId);
        message.setType("contact_student"); message.setContent(content.toString()); message.setRelatedId(jobId != null ? jobId : 0);
        String sendResult = messageService.sendMessage(message);
        if ("success".equals(sendResult)) return Map.of("success", true, "message", "已成功向" + studentName + "发送联系消息");
        return Map.of("success", false, "message", sendResult);
    }

    private Map<String, Object> executeApplyJob(SessionContext ctx, Map<String, Object> match) {
        Integer jobId = (Integer) match.get("id"); String jobTitle = (String) match.get("title");
        Job job = jobMapper.selectById(jobId);
        if (job == null) return Map.of("success", false, "message", "兼职已不存在");
        if ("已招满".equals(job.getStatus())) return Map.of("success", false, "message", "该兼职已招满");

        User student = userService.getUserById(ctx.userId);
        if (student == null) return Map.of("success", false, "message", "用户信息异常");
        if (student.getCredit() == null || student.getCredit() < 50) return Map.of("success", false, "message", "您的信用分低于50，无法申请兼职");
        if (student.getTimepreference() == null || student.getTimepreference().trim().isEmpty()) return Map.of("success", false, "message", "请先在个人中心设置您的空闲时间");

        com.xk.entity.JobApplication existing = jobApplicationMapper.getApplicationByJobAndStudent(jobId, ctx.userId);
        if (existing != null) {
            if ("accepted".equals(existing.getStatus())) {
                List<Map<String, Object>> activeAssignments = workAssignmentMapper.getActiveAssignmentsByJobIdAndStudent(jobId, ctx.userId);
                if (activeAssignments != null && !activeAssignments.isEmpty()) return Map.of("success", false, "message", "您当前仍在该兼职工作中，无法重复申请");
            } else if (!"rejected".equals(existing.getStatus())) return Map.of("success", false, "message", "您已经申请过该兼职（状态：" + existing.getStatus() + "）");
        }

        com.xk.entity.JobApplication application = new com.xk.entity.JobApplication();
        application.setJobId(jobId); application.setStudentId(ctx.userId); application.setMerchantId(job.getUserId());
        String studentName = (student != null && student.getName() != null) ? student.getName() : "学生";
        application.setStudentName(studentName); application.setCreditScore(student.getCredit() != null ? student.getCredit() : 60);
        if (student.getTimepreference() != null) application.setTimeAvailability(student.getTimepreference());
        application.setStatus("pending");

        String selectedDatesJson = buildSelectedDatesForApply(ctx.userId, job);
        application.setSelectedDates(selectedDatesJson);
        jobApplicationMapper.insertApplication(application);

        Message message = new Message(); message.setSenderId(ctx.userId); message.setReceiverId(job.getUserId());
        message.setType("apply_job"); message.setContent(buildApplyMessageContent(student, job, selectedDatesJson));
        message.setRelatedId(application.getId()); messageService.sendMessage(message);
        return Map.of("success", true, "message", "已成功申请兼职【" + jobTitle + "】，请等待商户审核");
    }

    private String buildSelectedDatesForApply(int studentId, Job job) {
        Set<String> studentFree = cacheClearService.getStudentAvailableSlots(studentId);
        Set<String> jobAvailable = cacheClearService.getJobAvailableSlots(job.getId());
        if (studentFree.isEmpty() || jobAvailable.isEmpty()) return "[]";
        LocalDate today = LocalDate.now(); LocalDate weekEnd = today.with(java.time.DayOfWeek.SUNDAY); LocalTime now = LocalTime.now();
        List<Map<String, String>> selected = new ArrayList<>();
        for (LocalDate d = today; !d.isAfter(weekEnd); d = d.plusDays(1)) {
            int day = d.getDayOfWeek().getValue();
            for (String slot : new String[]{"morning", "afternoon", "evening"}) {
                String key = day + "_" + slot;
                if (!studentFree.contains(key) || !jobAvailable.contains(key)) continue;
                if (d.equals(today)) { int startHour = "morning".equals(slot) ? 9 : "afternoon".equals(slot) ? 14 : 18; if (now.getHour() >= startHour) continue; }
                Map<String, String> item = new HashMap<>(); item.put("date", d.toString()); item.put("timeSlot", slot); selected.add(item);
            }
        }
        try { return objectMapper.writeValueAsString(selected); } catch (Exception e) { return "[]"; }
    }

    private String buildApplyMessageContent(User student, Job job, String selectedDatesJson) {
        StringBuilder sb = new StringBuilder();
        String name = (student != null && student.getName() != null) ? student.getName() : "学生";
        sb.append(name).append(" 通过智能匹配申请了您的兼职：").append(job.getTitle()).append("\n\n");
        if (selectedDatesJson != null && !selectedDatesJson.isEmpty() && !"[]".equals(selectedDatesJson)) {
            try {
                List<Map<String, String>> dates = objectMapper.readValue(selectedDatesJson, new TypeReference<>() {});
                if (!dates.isEmpty()) {
                    sb.append("申请时段：\n");
                    String[] dayNames = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
                    for (Map<String, String> d : dates) {
                        String dateStr = d.get("date"), slot = d.get("timeSlot");
                        String slotName = "morning".equals(slot) ? "上午" : "afternoon".equals(slot) ? "下午" : "晚上";
                        try { LocalDate ld = LocalDate.parse(dateStr); sb.append("  ").append(ld).append(" ").append(dayNames[ld.getDayOfWeek().getValue()]).append(" ").append(slotName).append("\n"); }
                        catch (Exception e) { sb.append("  ").append(dateStr).append(" ").append(slotName).append("\n"); }
                    }
                }
            } catch (Exception e) {}
        }
        sb.append("请及时处理"); return sb.toString();
    }



    private Map<String, Object> buildGapResult(String sessionId, int total, String summary, String hint) {
        Map<String, Object> result = new HashMap<>(); result.put("sessionId", sessionId); result.put("matches", Collections.emptyList()); result.put("total", total);
        if (summary != null) result.put("summary", summary); if (hint != null) result.put("hint", hint); return result;
    }

    private Map<String, Object> buildMerchantGapResult(User merchant) {
        String sessionId = UUID.randomUUID().toString();
        List<Job> merchantJobs = jobMapper.selectByUserId(merchant.getId());
        for (Job job : merchantJobs) cacheClearService.clearJobCache(job.getId());

        List<Map<String, Object>> unfilledSlots = scheduleMapper.getUnfilledMerchantRequirements(merchant.getId());
        if (unfilledSlots == null || unfilledSlots.isEmpty()) return buildGapResult(sessionId, 0, "当前所有兼职时段均已招满，暂无空缺。", "当前没有空缺需要填补");

        LocalDate today = LocalDate.now();
        Set<Integer> candidateStudentIds = new LinkedHashSet<>();
        Map<Integer, Set<String>> occupiedSlotsCache = new HashMap<>();
        for (Map<String, Object> slot : unfilledSlots) {
            Number dayN = (Number) slot.get("dayOfWeek"); String timeSlot = (String) slot.get("timeSlot");
            if (dayN == null || timeSlot == null) continue;
            int day = dayN.intValue(); int period = timeSlotToPeriod(timeSlot);
            List<Integer> ids = scheduleMapper.findStudentsByTimePreference(day, period);
            for (Integer studentId : ids) {
                Set<String> occupied = occupiedSlotsCache.get(studentId);
                if (occupied == null) {
                    List<Map<String, Object>> records = workAssignmentMapper.getOccupiedSlotsForStudent(studentId, today);
                    occupied = new HashSet<>();
                    for (Map<String, Object> rec : records) {
                        LocalDate workDate = rec.get("work_date") instanceof java.sql.Date ? ((java.sql.Date) rec.get("work_date")).toLocalDate() : (LocalDate) rec.get("work_date");
                        occupied.add(workDate.getDayOfWeek().getValue() + "_" + rec.get("time_slot"));
                    }
                    occupiedSlotsCache.put(studentId, occupied);
                }
                if (!occupied.contains(day + "_" + timeSlot)) candidateStudentIds.add(studentId);
            }
        }
        List<User> candidates = new ArrayList<>();
        for (Integer sid : candidateStudentIds) { User u = userService.getUserById(sid); if (u != null) candidates.add(u); }
        for (User u : candidates) cacheClearService.clearStudentCache(u.getId());
        if (candidates.isEmpty()) {
            int totalGap = unfilledSlots.stream().mapToInt(s -> ((Number) s.get("gapCount")).intValue()).sum();
            return buildGapResult(sessionId, 0, String.format("当前共有 %d 个时间段空缺。", totalGap), "没有符合条件的学生可推荐。");
        }

        String prompt = buildGapPrompt(merchant, candidates, unfilledSlots);
        String aiResponse = callAiWithSpring(prompt, 256);
        List<Map<String, Object>> matches = null; boolean aiSuccess = false;

        if (aiResponse != null && !aiResponse.trim().isEmpty()) {
            List<AIMatchResult> matchResults = parseSimpleMatchesResponse(aiResponse);
            if (matchResults != null && !matchResults.isEmpty()) {
                matches = new ArrayList<>();
                for (AIMatchResult r : matchResults) { Map<String, Object> item = buildMatchItem(r); if (item != null) { item.put("suggestedActions", defaultActions("student")); matches.add(item); } }
                sanitizeMatchItems(matches, "商户", merchant);
                matches.removeIf(item -> item.get("score") == null || ((Integer) item.get("score")) == 0);
                aiSuccess = !matches.isEmpty();
            }
        }
        if (!aiSuccess) { matches = buildDirectMatches(candidates, unfilledSlots); if (matches != null) { sanitizeMatchItems(matches, "商户", merchant); matches.removeIf(item -> item.get("score") == null || ((Integer) item.get("score")) == 0); } if (matches == null) matches = new ArrayList<>(); }

        List<Map<String, Object>> plans = new ArrayList<>();
        if (!matches.isEmpty()) { Map<String, Object> plan = new HashMap<>(); plan.put("strategy", aiSuccess ? "AI 智能匹配" : "本地匹配"); plan.put("matches", matches); plans.add(plan); }

        final List<Map<String, Object>> finalMatches = matches;
        sessionStore.put(sessionId, new SessionContext(merchant.getId(), "商户", "空缺匹配", finalMatches));
        try { matchingHistoryMapper.insert(merchant.getId(), "空缺匹配", objectMapper.writeValueAsString(finalMatches), finalMatches.size()); } catch (Exception e) {}

        int totalGap = unfilledSlots.stream().mapToInt(s -> ((Number) s.get("gapCount")).intValue()).sum();
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId); result.put("matches", finalMatches); result.put("plans", plans); result.put("total", finalMatches.size());
        return result;
    }

    private Map<String, Object> buildStudentGapResult(User student) {
        String sessionId = UUID.randomUUID().toString(); cacheClearService.clearStudentCache(student.getId());
        Set<String> freeSlotsSet = cacheClearService.getStudentAvailableSlots(student.getId());
        if (freeSlotsSet.isEmpty()) return buildGapResult(sessionId, 0, "你的所有空闲时段均已被兼职工作占用或已过期，暂无可匹配的兼职。", null);

        List<Job> jobs = getAvailableJobs(); if (jobs.isEmpty()) return buildGapResult(sessionId, 0, "当前暂无进行中的兼职。", null);

        Set<Integer> matchedJobIds = new LinkedHashSet<>();
        for (String key : freeSlotsSet) { String[] parts = key.split("_"); for (Job j : jobMapper.selectByDayAndSlot(Integer.parseInt(parts[0]), parts[1])) matchedJobIds.add(j.getId()); }
        List<Job> candidates = new ArrayList<>(); for (Integer jid : matchedJobIds) { Job j = jobMapper.selectById(jid); if (j != null && "进行中".equals(j.getStatus()) && !"已招满".equals(j.getStatus())) candidates.add(j); }
        if (candidates.isEmpty()) candidates = jobs;

        List<Map<String, Object>> matches = null; boolean aiSuccess = false;
        try { String aiResponse = callAiWithSpring(buildStudentGapPrompt(student, candidates, freeSlotsSet), 256);
            if (aiResponse != null) { List<AIMatchResult> mr = parseSimpleMatchesResponse(aiResponse); if (mr != null && !mr.isEmpty()) { matches = new ArrayList<>(); for (AIMatchResult r : mr) { Map<String, Object> item = buildMatchItem(r); if (item != null) { item.put("suggestedActions", defaultActions("job")); matches.add(item); } } sanitizeMatchItems(matches, "学生", student); matches.removeIf(item -> item.get("score") == null || ((Integer) item.get("score")) == 0); aiSuccess = !matches.isEmpty(); } }
        } catch (Exception e) {}
        if (!aiSuccess) { matches = buildDirectJobMatches(candidates, freeSlotsSet); if (matches != null) { sanitizeMatchItems(matches, "学生", student); matches.removeIf(item -> item.get("score") == null || ((Integer) item.get("score")) == 0); } if (matches == null) matches = new ArrayList<>(); }

        List<Map<String, Object>> plans = new ArrayList<>(); if (!matches.isEmpty()) { Map<String, Object> plan = new HashMap<>(); plan.put("strategy", aiSuccess ? "AI智能匹配" : "本地匹配"); plan.put("matches", matches); plans.add(plan); }
        final List<Map<String, Object>> fm = matches;
        sessionStore.put(sessionId, new SessionContext(student.getId(), "学生", "空缺匹配", fm));
        try { matchingHistoryMapper.insert(student.getId(), "空缺匹配", objectMapper.writeValueAsString(fm), fm.size()); } catch (Exception e) {}

        Map<String, Object> result = new HashMap<>(); result.put("sessionId", sessionId); result.put("matches", fm); result.put("plans", plans); result.put("total", fm.size());
        return result;
    }

    private List<Map<String, Object>> buildDirectMatches(List<User> candidates, List<Map<String, Object>> unfilledSlots) {
        List<Map<String, Object>> matches = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) return matches;
        LocalDate today = LocalDate.now();
        int currentDay = today.getDayOfWeek().getValue(), currentHour = LocalDateTime.now().getHour(), currentMinute = LocalDateTime.now().getMinute();
        Set<String> gapSet = new HashSet<>();
        for (Map<String, Object> slot : unfilledSlots) {
            int day = ((Number) slot.get("dayOfWeek")).intValue(); String ts = (String) slot.get("timeSlot");
            if (day < currentDay) continue;
            if (day == currentDay) { int startHour = "morning".equals(ts) ? 9 : "afternoon".equals(ts) ? 14 : 18; if (currentHour > startHour || (currentHour == startHour && currentMinute >= 0)) continue; }
            gapSet.add(day + "_" + ts);
        }
        if (gapSet.isEmpty()) return matches;

        Map<Integer, Set<String>> occupiedMap = new HashMap<>();
        for (User s : candidates) {
            List<Map<String, Object>> records = workAssignmentMapper.getOccupiedSlotsForStudent(s.getId(), today);
            Set<String> occupied = new HashSet<>();
            for (Map<String, Object> rec : records) { LocalDate wd = ((java.sql.Date) rec.get("work_date")).toLocalDate(); occupied.add(wd.getDayOfWeek().getValue() + "_" + rec.get("time_slot")); }
            occupiedMap.put(s.getId(), occupied);
        }
        for (User s : candidates) {
            Set<String> occupied = occupiedMap.getOrDefault(s.getId(), Collections.emptySet());
            Set<String> freePrefSet = new HashSet<>();
            if (s.getTimepreference() != null) { try { List<Map<String, Object>> prefs = objectMapper.readValue(s.getTimepreference(), new TypeReference<>() {}); for (Map<String, Object> p : prefs) { int day = ((Number) p.get("day")).intValue(); int pn = ((Number) p.get("period")).intValue(); freePrefSet.add(day + "_" + (pn == 1 ? "morning" : pn == 2 ? "afternoon" : "evening")); } } catch (Exception e) {} }
            int overlap = 0; for (String gap : gapSet) if (freePrefSet.contains(gap) && !occupied.contains(gap)) overlap++;
            if (overlap == 0) continue;
            int score = Math.min(100, overlap * 100 / gapSet.size() + (s.getCredit() != null ? s.getCredit() / 10 : 6));
            Map<String, Object> item = new HashMap<>(); item.put("type", "student"); item.put("id", s.getId()); item.put("name", s.getName() != null ? s.getName() : "未知"); item.put("credit", s.getCredit() != null ? s.getCredit() : 60); item.put("timepreference", s.getTimepreference()); item.put("score", Math.max(10, score)); item.put("url", "/profile?id=" + s.getId()); item.put("suggestedActions", defaultActions("student"));
            matches.add(item);
        }
        matches.sort((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")));
        return matches;
    }

    private List<Map<String, Object>> buildDirectJobMatches(List<Job> jobs, Set<String> freeSet) {
        List<Map<String, Object>> matches = new ArrayList<>();
        if (freeSet == null || freeSet.isEmpty()) return matches;
        for (Job j : jobs) {
            Set<String> jobAvailable = cacheClearService.getJobAvailableSlots(j.getId()); if (jobAvailable.isEmpty()) continue;
            int overlapCount = 0; for (String free : freeSet) if (jobAvailable.contains(free)) overlapCount++;
            if (overlapCount == 0) continue;
            Map<String, Object> item = new HashMap<>(); item.put("type", "job"); item.put("id", j.getId()); item.put("title", j.getTitle()); item.put("salary", j.getSalary()); item.put("score", Math.min(100, overlapCount * 100 / jobAvailable.size())); item.put("url", "/job/" + j.getId()); item.put("suggestedActions", defaultActions("job"));
            matches.add(item);
        }
        matches.sort((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")));
        return matches;
    }

    private List<Map<String, Object>> invokeAiForMatches(String prompt, String matchType, List<?> candidates, int userId, String identity, String sessionId) { return null; }
    private List<Map<String, Object>> defaultActions(String matchType) {
        return "student".equals(matchType)
            ? List.of(Map.of("action", "contact_student", "label", "联系TA"), Map.of("action", "view_detail", "label", "查看详情"))
            : List.of(Map.of("action", "apply_job", "label", "立即申请"), Map.of("action", "view_detail", "label", "查看详情"));
    }



    private String callAiWithSpring(String prompt, int maxTokens) {
        try {
            String response = chatClient.prompt().user(prompt)
                .options(OllamaOptions.builder()
                    .model("qwen2.5:3b")
                    .numPredict(maxTokens)
                    .numCtx(2048)
                    .temperature(0.0)
                    .format("json")
                    .build())
                .call().content();
            if (response != null && response.contains("id") && response.contains("score")) return response;
        } catch (Exception e) {
            System.err.println("AI调用失败: " + e.getMessage());
        }
        return null;
    }

    private String callAi(String prompt, Integer numPredict) { return callAiWithSpring(prompt, numPredict != null ? numPredict : 512); }
    private String callAiFast(String prompt) { return callAiWithSpring(prompt, 256); }



    private List<AIMatchResult> parseSimpleMatchesResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) return new ArrayList<>();
        String json = extractJson(aiResponse); if (json == null) return new ArrayList<>();
        json = json.replaceAll("```json\\s*|```", "").replaceAll("\\}\\s*\\{", "},{").replaceAll(",\\s*\\}", "}");
        List<AIMatchResult> results = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"score\"\\s*:\\s*(\\d+)").matcher(json);
        while (matcher.find()) { AIMatchResult r = new AIMatchResult(); r.setId(Integer.parseInt(matcher.group(1))); r.setScore(Integer.parseInt(matcher.group(2))); results.add(r); }
        if (!results.isEmpty()) return results;
        try { Map<String, Object> wrapper = objectMapper.readValue(json, new TypeReference<>() {}); Object matchesObj = wrapper.get("matches"); if (matchesObj != null) { for (Map<String, Object> m : objectMapper.readValue(objectMapper.writeValueAsString(matchesObj), new TypeReference<List<Map<String, Object>>>() {})) { AIMatchResult r = new AIMatchResult(); r.setId((Integer) m.get("id")); r.setScore(m.get("score") instanceof Number ? ((Number) m.get("score")).intValue() : 50); results.add(r); } } } catch (Exception e) {}
        return results;
    }

    private List<AIMatchResult> parseAiResponse(String aiResponse) { return parseSimpleMatchesResponse(aiResponse); }

    private List<Map<String, Object>> parsePlansResponse(String cleanedResponse, String identity) {
        List<Map<String, Object>> plans = new ArrayList<>();
        if (cleanedResponse == null || cleanedResponse.trim().isEmpty()) return plans;
        try {
            String json = cleanedResponse; int start = json.indexOf('{'), end = json.lastIndexOf('}'); if (start == -1 || end == -1 || end <= start) return plans;
            json = json.substring(start, end + 1); json = repairJson(json);
            Map<String, Object> wrapper = objectMapper.readValue(json, new TypeReference<>() {});
            Object plansObj = wrapper.get("plans"); if (plansObj != null) { /* skip complex parsing, use fallback */ }
        } catch (Exception e) {}
        if (plans.isEmpty()) {
            try { List<AIMatchResult> matchResults = parseAiResponse(repairJson(cleanedResponse)); if (matchResults != null) { List<Map<String, Object>> flat = new ArrayList<>(); for (AIMatchResult r : matchResults) { Map<String, Object> item = buildMatchItem(r); if (item != null) flat.add(item); } if (!flat.isEmpty()) { Map<String, Object> p = new HashMap<>(); p.put("matches", flat); plans.add(p); } } } catch (Exception e2) {}
        }
        return plans;
    }

    private List<Map<String, Object>> extractPlansFromMalformedJson(String rawText, String identity) { return Collections.emptyList(); }
    private List<Map<String, Object>> flattenMatches(List<Map<String, Object>> plans) { List<Map<String, Object>> flat = new ArrayList<>(); for (Map<String, Object> plan : plans) { @SuppressWarnings("unchecked") List<Map<String, Object>> pm = (List<Map<String, Object>>) plan.get("matches"); if (pm != null) flat.addAll(pm); } return flat; }

    private Map<String, Object> buildMatchItem(AIMatchResult r) {
        Map<String, Object> item = new HashMap<>();
        String type = r.getType();
        if (type == null) { User u = userService.getUserById(r.getId()); if (u != null && "学生".equals(u.getIdentity())) type = "student"; else { Job j = jobMapper.selectById(r.getId()); if (j != null) type = "job"; } }
        if (type == null) return null;
        item.put("type", type); item.put("id", r.getId()); item.put("score", Math.max(0, Math.min(100, r.getScore())));
        try {
            if ("student".equals(type)) { User u = userService.getUserById(r.getId()); if (u == null || !"学生".equals(u.getIdentity())) return null; item.put("name", u.getName() != null ? u.getName() : "未知"); item.put("phone", u.getPhone() != null ? u.getPhone() : "未绑定"); item.put("credit", u.getCredit() != null ? u.getCredit() : 60); item.put("timepreference", u.getTimepreference()); item.put("url", "/profile?id=" + u.getId()); }
            else if ("job".equals(type)) { Job j = jobMapper.selectById(r.getId()); if (j == null || j.getTitle() == null) return null; item.put("title", j.getTitle()); item.put("salary", j.getSalary() != null ? j.getSalary() : "面议"); item.put("address", j.getAddress() != null ? j.getAddress() : "未指定"); item.put("time", formatJobTime(j.getTime())); item.put("url", "/job/" + j.getId()); }
        } catch (Exception e) { return null; }
        return item;
    }

    private String cleanAiResponse(String aiResponse) {
        String text = aiResponse.trim();
        Matcher mdMatcher = Pattern.compile("```json\\s*(.*?)\\s*```", Pattern.DOTALL).matcher(text);
        if (mdMatcher.find()) text = mdMatcher.group(1).trim(); else text = text.replaceAll("```\\s*json\\s*|```", "").trim();
        text = text.replaceAll("\"score\":\\s*(\\d+)\\.\\d*\\.\\.\\.\\d*", "\"score\":$1").replaceAll("\"score\":\\s*(\\d+)\\.\\.\\.\\d*", "\"score\":$1");
        text = text.replaceAll("\"type\":\"user\\d+\"", "\"type\":\"student\"").replaceAll("\"type\":\"job\\d+\"", "\"type\":\"job\"");
        int jsonStart = -1; for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); if (c == '{' || c == '[') { jsonStart = i; break; } }
        if (jsonStart == -1) return text;
        return extractBalancedJson(text.substring(jsonStart));
    }

    private String extractBalancedJson(String text) {
        if (text == null || text.isEmpty()) return text;
        int depth = 0; char openChar = text.charAt(0), closeChar = openChar == '{' ? '}' : ']'; boolean inString = false, escaped = false;
        for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); if (escaped) { escaped = false; continue; } if (c == '\\' && inString) { escaped = true; continue; } if (c == '"') { inString = !inString; continue; } if (inString) continue; if (c == '{' || c == '[') depth++; if (c == '}' || c == ']') { depth--; if (depth == 0 && c == closeChar) return text.substring(0, i + 1); } }
        return text;
    }

    private String repairJson(String json) {
        if (json == null || json.isEmpty()) return json;
        String trimmed = json.trim(); int braceOpen = 0, braceClose = 0, bracketOpen = 0, bracketClose = 0; boolean inString = false, escaped = false;
        for (int i = 0; i < trimmed.length(); i++) { char c = trimmed.charAt(i); if (escaped) { escaped = false; continue; } if (c == '\\' && inString) { escaped = true; continue; } if (c == '"') { inString = !inString; continue; } if (inString) continue; if (c == '{') braceOpen++; if (c == '}') braceClose++; if (c == '[') bracketOpen++; if (c == ']') bracketClose++; }
        StringBuilder sb = new StringBuilder(trimmed); for (int i = 0; i < bracketOpen - bracketClose; i++) sb.append(']'); for (int i = 0; i < braceOpen - braceClose; i++) sb.append('}');
        return fixScorePercentSigns(quoteJsonKeys(sb.toString()));
    }

    private String quoteJsonKeys(String json) {
        if (json == null || json.isEmpty()) return json;
        StringBuilder sb = new StringBuilder(); int i = 0, len = json.length(); boolean inString = false, inKey = false; int keyStart = -1;
        while (i < len) { char c = json.charAt(i); if (inString) { if (c == '\\' && i + 1 < len) { sb.append(c).append(json.charAt(i + 1)); i += 2; continue; } if (c == '"') inString = false; sb.append(c); i++; continue; } if (c == '"') { inString = true; sb.append(c); i++; continue; } if (c == '{' || c == ',' || c == '[') { sb.append(c); i++; while (i < len && Character.isWhitespace(json.charAt(i))) { sb.append(json.charAt(i)); i++; } if (i < len && json.charAt(i) != '"' && json.charAt(i) != '{' && json.charAt(i) != '[' && json.charAt(i) != ']') { inKey = true; keyStart = sb.length(); sb.append('"'); } continue; } if (inKey && c == ':') { sb.append('"'); inKey = false; sb.append(':'); i++; continue; } if (inKey && (c == ',' || c == '}' || c == ']')) { sb.insert(keyStart, '"'); inKey = false; sb.append('"'); sb.append(c); i++; continue; } sb.append(c); i++; }
        if (inKey) { sb.append('"'); sb.insert(keyStart, '"'); }
        return sb.toString();
    }

    private String fixScorePercentSigns(String json) { return json == null ? null : json.replaceAll("\"score\"\\s*:\\s*(\\d+(?:\\.\\d+)?)%", "\"score\":$1"); }
    private String extractThinkingFromResponse(String response) { if (response == null) return ""; Matcher m = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL).matcher(response); if (m.find()) return m.group(1).trim(); m = Pattern.compile("```think\\s*(.*?)\\s*```", Pattern.DOTALL).matcher(response); if (m.find()) return m.group(1).trim(); return ""; }
    private String removeThinkTags(String response) { if (response == null) return ""; return response.replaceAll("(?s)<think>.*?</think>", "").replaceAll("```think\\s*.*?\\s*```", "").trim(); }
    private String extractThinking(String aiResponse) { return ""; }
    private String extractThinkingProperly(String response) { return extractThinkingFromResponse(response); }



    @SuppressWarnings("unchecked")
    private void sanitizeMatchItems(List<Map<String, Object>> items, String identity) { sanitizeMatchItems(items, identity, null, Collections.emptySet()); }
    @SuppressWarnings("unchecked")
    private void sanitizeMatchItems(List<Map<String, Object>> items, String identity, User currentUser) { sanitizeMatchItems(items, identity, currentUser, Collections.emptySet()); }
    @SuppressWarnings("unchecked")
    private void sanitizeMatchItems(List<Map<String, Object>> items, String identity, User currentUser, Set<String> requiredSlots) {
        if (items == null) return;
        items.removeIf(item -> { Object s = item.get("score"); return s instanceof Number && ((Number) s).intValue() == 0; });
        items.removeIf(item -> {
            if (!"student".equals(item.get("type"))) return false;
            Object tp = item.get("timepreference");
            return tp == null || tp.toString().trim().isEmpty();
        });
        Set<String> userFreeSlots = currentUser != null && currentUser.getTimepreference() != null ? extractFreeSlotsFromUser(currentUser) : Collections.emptySet();
        for (Map<String, Object> item : items) {
            int rawScore = item.get("score") instanceof Number ? ((Number) item.get("score")).intValue() : 50;
            int recomputed = recomputeMatchScore(item, identity, currentUser, requiredSlots);
            item.put("score", recomputed >= 0 ? Math.max(0, Math.min(100, recomputed)) : Math.max(0, Math.min(100, rawScore)));
            if (item.get("reason") == null || item.get("reason").toString().isEmpty()) item.put("reason", buildClearReason(item, identity, userFreeSlots, requiredSlots));
            String type = (String) item.get("type");
            if ("商户".equals(identity) || "管理员".equals(identity)) {
                if (!"student".equals(type)) { item.put("type", "student"); type = "student"; }
            } else if ("学生".equals(identity)) {
                if (!"job".equals(type)) { item.put("type", "job"); type = "job"; }
            }
            if ("student".equals(type) && item.get("name") == null) {
                User u = userService.getUserById((Integer) item.get("id"));
                if (u != null && "学生".equals(u.getIdentity())) { item.put("name", u.getName() != null ? u.getName() : "未知"); item.put("phone", u.getPhone() != null ? u.getPhone() : "未绑定"); item.put("credit", u.getCredit() != null ? u.getCredit() : 60); item.put("timepreference", u.getTimepreference()); item.put("url", "/profile?id=" + u.getId()); }
            }
            if ("job".equals(type) && item.get("title") == null) {
                Job j = jobMapper.selectById((Integer) item.get("id"));
                if (j != null && j.getTitle() != null) { item.put("title", j.getTitle()); item.put("salary", j.getSalary() != null ? j.getSalary() : "面议"); item.put("address", j.getAddress() != null ? j.getAddress() : "未指定"); item.put("time", formatJobTime(j.getTime())); item.put("url", "/job/" + j.getId()); }
            }
            item.put("suggestedActions", defaultActions(type));
        }
    }

    private String buildClearReason(Map<String, Object> item, String identity, Set<String> userFreeSlots, Set<String> requiredSlots) {
        String type = (String) item.get("type");
        if ("student".equals(type)) { Object c = item.get("credit"); int credit = c instanceof Number ? ((Number) c).intValue() : 0; return "信用分 " + credit; }
        return "智能推荐";
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractFreeSlotsFromUser(User user) {
        Set<String> slots = new HashSet<>();
        try { for (Map<String, Object> pref : objectMapper.readValue(user.getTimepreference(), new TypeReference<List<Map<String, Object>>>() {})) { int day = ((Number) pref.get("day")).intValue(); int period = ((Number) pref.get("period")).intValue(); slots.add(day + "_" + (period == 1 ? "morning" : period == 2 ? "afternoon" : "evening")); } } catch (Exception e) {}
        return slots;
    }

    @SuppressWarnings("unchecked")
    private int recomputeMatchScore(Map<String, Object> item, String identity, User currentUser, Set<String> requiredSlots) {
        String type = (String) item.get("type");
        if ("student".equals(type) && !requiredSlots.isEmpty()) { Integer sid = (Integer) item.get("id"); if (sid == null) return -1; Set<String> available = cacheClearService.getStudentAvailableSlots(sid); int overlap = 0; for (String req : requiredSlots) if (available.contains(req)) overlap++; return requiredSlots.isEmpty() ? 0 : Math.min(100, overlap * 100 / requiredSlots.size()); }
        if ("job".equals(type) && currentUser != null && "学生".equals(currentUser.getIdentity())) { Set<String> studentFree = cacheClearService.getStudentAvailableSlots(currentUser.getId()); Integer jid = (Integer) item.get("id"); if (jid == null) return -1; Set<String> jobAvail = cacheClearService.getJobAvailableSlots(jid); if (jobAvail.isEmpty()) return 0; int overlap = 0; for (String f : studentFree) if (jobAvail.contains(f)) overlap++; return Math.min(100, overlap * 100 / jobAvail.size()); }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private int[] countStudentSlotOverlap(Object tpObj, Set<String> requiredSlots) { return null; }
    private Set<String> timeSlotsToSet(List<TimeSlotExtractor.TimeSlot> timeSlots) { Set<String> set = new HashSet<>(); if (timeSlots != null) for (TimeSlotExtractor.TimeSlot ts : timeSlots) if (ts.dayOfWeek != null && ts.timeSlot != null) set.add(ts.dayOfWeek + "_" + ts.timeSlot); return set; }



    private List<User> getAvailableStudents() { return userService.getAvailableStudents().stream().filter(u -> u.getCredit() != null && u.getCredit() >= 50 && (u.getIsPenalty() == null || u.getIsPenalty() == 0) && u.getTimepreference() != null && !u.getTimepreference().trim().isEmpty()).collect(Collectors.toList()); }
    private List<Job> getAvailableJobs() { return jobMapper.selectAll().stream().filter(j -> "进行中".equals(j.getStatus()) && !"已招满".equals(j.getStatus())).collect(Collectors.toList()); }

    private List<User> filterStudentsBySlots(List<TimeSlotExtractor.TimeSlot> slots) {
        if (slots.isEmpty()) return getAvailableStudents();
        Set<Integer> candidateIds = new LinkedHashSet<>();
        for (TimeSlotExtractor.TimeSlot ts : slots) {
            if (ts.dayOfWeek != null && ts.timeSlot != null) candidateIds.addAll(scheduleMapper.findAvailableStudentIds(ts.dayOfWeek, ts.timeSlot));
            else if (ts.dayOfWeek != null) for (Map<String, Object> s : scheduleMapper.getStudentsByDay(ts.dayOfWeek)) candidateIds.add(((Number) s.get("id")).intValue());
            else if (ts.timeSlot != null) for (int d = 1; d <= 7; d++) candidateIds.addAll(scheduleMapper.findAvailableStudentIds(d, ts.timeSlot));
        }
        Set<Integer> seen = new LinkedHashSet<>(); List<User> result = new ArrayList<>();
        for (Integer sid : candidateIds) { if (seen.contains(sid)) continue; Set<String> available = cacheClearService.getStudentAvailableSlots(sid); boolean match = false;
            for (TimeSlotExtractor.TimeSlot ts : slots) { if (ts.dayOfWeek != null && ts.timeSlot != null) { if (available.contains(ts.dayOfWeek + "_" + ts.timeSlot)) { match = true; break; } } else if (ts.dayOfWeek != null) { for (int p = 1; p <= 3; p++) { if (available.contains(ts.dayOfWeek + "_" + (p == 1 ? "morning" : p == 2 ? "afternoon" : "evening"))) { match = true; break; } } if (match) break; } else if (ts.timeSlot != null) { for (int d = 1; d <= 7; d++) { if (available.contains(d + "_" + ts.timeSlot)) { match = true; break; } } if (match) break; } }
            if (match) { seen.add(sid); User u = userService.getUserById(sid); if (u != null && u.getCredit() != null && u.getCredit() >= 50 && u.getTimepreference() != null && !u.getTimepreference().trim().isEmpty()) result.add(u); }
        }
        return result.isEmpty() ? getAvailableStudents() : result;
    }

    private List<Job> filterJobsBySlots(List<TimeSlotExtractor.TimeSlot> slots) {
        if (slots.isEmpty()) return getAvailableJobs();
        Set<Integer> candidateIds = new LinkedHashSet<>();
        for (TimeSlotExtractor.TimeSlot ts : slots) { if (ts.dayOfWeek != null && ts.timeSlot != null) for (Job j : jobMapper.selectByDayAndSlot(ts.dayOfWeek, ts.timeSlot)) candidateIds.add(j.getId()); else if (ts.dayOfWeek != null) for (Job j : jobMapper.selectByDay(ts.dayOfWeek)) candidateIds.add(j.getId()); else if (ts.timeSlot != null) for (int d = 1; d <= 7; d++) for (Job j : jobMapper.selectByDayAndSlot(d, ts.timeSlot)) candidateIds.add(j.getId()); }
        Set<Integer> seen = new LinkedHashSet<>(); List<Job> result = new ArrayList<>();
        for (Integer jid : candidateIds) { if (seen.contains(jid)) continue; Set<String> available = cacheClearService.getJobAvailableSlots(jid); boolean match = false;
            for (TimeSlotExtractor.TimeSlot ts : slots) { if (ts.dayOfWeek != null && ts.timeSlot != null) { if (available.contains(ts.dayOfWeek + "_" + ts.timeSlot)) { match = true; break; } } else if (ts.dayOfWeek != null) { for (int p = 1; p <= 3; p++) { if (available.contains(ts.dayOfWeek + "_" + (p == 1 ? "morning" : p == 2 ? "afternoon" : "evening"))) { match = true; break; } } if (match) break; } else if (ts.timeSlot != null) { for (int d = 1; d <= 7; d++) { if (available.contains(d + "_" + ts.timeSlot)) { match = true; break; } } if (match) break; } }
            if (match) { seen.add(jid); Job j = jobMapper.selectById(jid); if (j != null && "进行中".equals(j.getStatus())) result.add(j); }
        }
        return result.isEmpty() ? getAvailableJobs() : result;
    }

    private Map<User, Integer> ragPreScoreStudents(List<User> candidates, Set<String> requiredSlots) {
        Map<User, Integer> scoreMap = new LinkedHashMap<>();
        if (candidates == null || candidates.isEmpty()) return scoreMap;
        for (User s : candidates) {
            Set<String> available = cacheClearService.getStudentAvailableSlots(s.getId());
            int overlap = 0;
            if (requiredSlots != null && !requiredSlots.isEmpty()) {
                for (String req : requiredSlots) if (available.contains(req)) overlap++;
            }
            int credit = s.getCredit() != null ? s.getCredit() : 60;
            int score = requiredSlots != null && !requiredSlots.isEmpty()
                ? Math.min(100, overlap * 100 / requiredSlots.size() + credit / 10)
                : credit;
            scoreMap.put(s, score);
        }
        scoreMap.entrySet().stream()
            .sorted(Map.Entry.<User, Integer>comparingByValue().reversed())
            .forEachOrdered(e -> {});
        return scoreMap;
    }

    private Map<Job, Integer> ragPreScoreJobs(List<Job> candidates, Set<String> studentFreeSlots) {
        Map<Job, Integer> scoreMap = new LinkedHashMap<>();
        if (candidates == null || candidates.isEmpty()) return scoreMap;
        for (Job j : candidates) {
            Set<String> jobAvail = cacheClearService.getJobAvailableSlots(j.getId());
            if (jobAvail.isEmpty()) continue;
            int overlap = 0;
            if (studentFreeSlots != null) {
                for (String free : studentFreeSlots) if (jobAvail.contains(free)) overlap++;
            }
            if (overlap == 0 && studentFreeSlots != null && !studentFreeSlots.isEmpty()) continue;
            int score = studentFreeSlots != null && !studentFreeSlots.isEmpty()
                ? Math.min(100, overlap * 100 / Math.max(1, jobAvail.size()))
                : 50;
            scoreMap.put(j, score);
        }
        return scoreMap;
    }

    private List<User> filterStudentsByRealAvailability(List<User> candidates, List<TimeSlotExtractor.TimeSlot> requiredSlots) {
        if (candidates == null || candidates.isEmpty() || requiredSlots.isEmpty()) return candidates;
        Set<String> requiredSet = new HashSet<>(); for (TimeSlotExtractor.TimeSlot ts : requiredSlots) if (ts.dayOfWeek != null && ts.timeSlot != null) requiredSet.add(ts.dayOfWeek + "_" + ts.timeSlot);
        if (requiredSet.isEmpty()) return candidates;
        List<User> result = new ArrayList<>();
        for (User s : candidates) { Set<String> available = cacheClearService.getStudentAvailableSlots(s.getId()); for (String req : requiredSet) if (available.contains(req)) { result.add(s); break; } }
        return result;
    }

    private List<Job> filterJobsByRealAvailability(List<Job> candidates, List<TimeSlotExtractor.TimeSlot> freeSlots) {
        if (candidates == null || candidates.isEmpty() || freeSlots.isEmpty()) return candidates;
        Set<String> freeSet = new HashSet<>(); for (TimeSlotExtractor.TimeSlot ts : freeSlots) if (ts.dayOfWeek != null && ts.timeSlot != null) freeSet.add(ts.dayOfWeek + "_" + ts.timeSlot);
        if (freeSet.isEmpty()) return candidates;
        List<Job> result = new ArrayList<>();
        for (Job j : candidates) { Set<String> jobAvail = cacheClearService.getJobAvailableSlots(j.getId()); for (String f : freeSet) if (jobAvail.contains(f)) { result.add(j); break; } }
        return result;
    }



    private List<Map<String, Object>> buildLocalStudentMatches(List<User> students, Set<String> requiredSlots) {
        List<Map<String, Object>> matches = new ArrayList<>();
        if (students == null || students.isEmpty()) return matches;
        boolean hasSlots = requiredSlots != null && !requiredSlots.isEmpty();
        for (User s : students) {
            int score;
            if (hasSlots) {
                Set<String> available = cacheClearService.getStudentAvailableSlots(s.getId());
                int overlap = 0;
                for (String req : requiredSlots) if (available.contains(req)) overlap++;
                score = Math.min(100, overlap * 100 / requiredSlots.size() + (s.getCredit() != null ? s.getCredit() / 10 : 6));
            } else {
                score = Math.min(100, (s.getCredit() != null ? s.getCredit() : 60));
            }
            Map<String, Object> item = new HashMap<>(); item.put("type", "student"); item.put("id", s.getId()); item.put("name", s.getName() != null ? s.getName() : "未知"); item.put("credit", s.getCredit() != null ? s.getCredit() : 60); item.put("timepreference", s.getTimepreference()); item.put("score", Math.max(10, score)); item.put("url", "/profile?id=" + s.getId()); item.put("suggestedActions", defaultActions("student")); matches.add(item);
        }
        matches.sort((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")));
        return matches;
    }

    private List<Map<String, Object>> buildLocalJobMatches(List<Job> jobs, Set<String> studentFreeSlots) {
        List<Map<String, Object>> matches = new ArrayList<>();
        if (jobs == null || jobs.isEmpty()) return matches;
        boolean hasSlots = studentFreeSlots != null && !studentFreeSlots.isEmpty();
        for (Job j : jobs) {
            int score;
            if (hasSlots) {
                Set<String> jobAvailable = cacheClearService.getJobAvailableSlots(j.getId());
                if (jobAvailable.isEmpty()) continue;
                int overlap = 0;
                for (String free : studentFreeSlots) if (jobAvailable.contains(free)) overlap++;
                if (overlap == 0) continue;
                score = Math.min(100, overlap * 100 / jobAvailable.size());
            } else {
                score = 60;
            }
            Map<String, Object> item = new HashMap<>(); item.put("type", "job"); item.put("id", j.getId()); item.put("title", j.getTitle()); item.put("salary", j.getSalary()); item.put("score", score); item.put("url", "/job/" + j.getId()); item.put("suggestedActions", defaultActions("job")); matches.add(item);
        }
        matches.sort((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")));
        return matches;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getActualFreeSlots(User student) {
        if (student == null || student.getTimepreference() == null || student.getTimepreference().trim().isEmpty()) return null;
        List<Map<String, Object>> prefSlots = new ArrayList<>();
        try { for (Map<String, Object> slot : objectMapper.readValue(student.getTimepreference(), new TypeReference<List<Map<String, Object>>>() {})) { int day = ((Number) slot.get("day")).intValue(); int period = ((Number) slot.get("period")).intValue(); Map<String, Object> free = new HashMap<>(); free.put("dayOfWeek", day); free.put("timeSlot", period == 1 ? "morning" : period == 2 ? "afternoon" : "evening"); prefSlots.add(free); } } catch (Exception e) { return null; }
        return prefSlots;
    }



    private String buildRagAgentPrompt(String userInput, List<?> candidates, List<? extends Map.Entry<?, Integer>> scoredEntries, Set<String> userSlots, String matchType, String identity, String timeHint, User currentUser) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是校园兼职平台的智能匹配助手。根据用户需求，从候选列表中选出最佳匹配。\n\n");
        sb.append("### 输出格式\n{\"matches\":[{\"id\":数字,\"score\":整数}]}\nscore是0-100，只输出JSON。\n\n");
        if (!timeHint.isEmpty()) sb.append("### 系统预筛选\n").append(timeHint).append("\n\n");

        sb.append("### 候选列表（已按本地相关性预排序，仅供参考）：\n");
        if ("student".equals(matchType)) {
            for (int i = 0; i < candidates.size(); i++) {
                User s = (User) candidates.get(i);
                int ragScore = i < scoredEntries.size() ? scoredEntries.get(i).getValue() : 50;
                sb.append(String.format("%d. ID:%d %s 信用:%d 时段:%s [本地相关度:%d]\n",
                    i + 1, s.getId(), s.getName(), s.getCredit() != null ? s.getCredit() : 60,
                    formatTimePref(s.getTimepreference()), ragScore));
            }
        } else {
            for (int i = 0; i < candidates.size(); i++) {
                Job j = (Job) candidates.get(i);
                int ragScore = i < scoredEntries.size() ? scoredEntries.get(i).getValue() : 50;
                sb.append(String.format("%d. ID:%d %s 薪资:%s 时间:%s 地点:%s [本地相关度:%d]\n",
                    i + 1, j.getId(), j.getTitle(), j.getSalary(),
                    formatJobTime(j.getTime()), j.getAddress(), ragScore));
            }
        }
        sb.append("\n### 用户需求\n").append(userInput).append("\n");
        sb.append("请根据用户需求重新评估，选出最匹配的3-5个，输出JSON。");
        return sb.toString();
    }

    private String buildAgentPrompt(String userInput, List<User> students, List<Job> jobs, String identity, String timeHint, User currentUser) {
        StringBuilder sb = new StringBuilder(); sb.append("你是校园兼职平台的智能匹配助手。\n\n");
        sb.append("### 输出格式要求\n{\"matches\":[{\"id\":数字,\"score\":整数}]}\nscore是0-100的整数。只输出JSON。\n\n");
        if (!timeHint.isEmpty()) sb.append("系统已过滤时间约束: ").append(timeHint).append("\n\n");
        sb.append("### 可用数据：\n");
        if ("商户".equals(identity) || "管理员".equals(identity)) { for (User s : students) sb.append(String.format("- ID:%d 姓名:%s 空闲:%s 信用分:%d\n", s.getId(), s.getName(), formatTimePref(s.getTimepreference()), s.getCredit())); }
        else { for (Job j : jobs) sb.append(String.format("- ID:%d 标题:%s 薪资:%s 时间:%s\n", j.getId(), j.getTitle(), j.getSalary(), formatJobTime(j.getTime()))); }
        sb.append("\n### 用户输入：\n").append(userInput).append("\n");
        return sb.toString();
    }

    private String buildGapPrompt(User merchant, List<User> candidates, List<Map<String, Object>> unfilledSlots) {
        StringBuilder sb = new StringBuilder(); sb.append("你是校园兼职平台的智能匹配助手。商户有空缺时段。\n\n");
        for (Map<String, Object> slot : unfilledSlots) { int day = ((Number) slot.get("dayOfWeek")).intValue(); String ts = (String) slot.get("timeSlot"); sb.append(day).append(ts).append("\n"); }
        for (User s : candidates) sb.append("ID:").append(s.getId()).append(" 姓名:").append(s.getName()).append(" 信用:").append(s.getCredit()).append("\n");
        sb.append("只输出{\"matches\":[{\"id\":ID,\"score\":整数}]}");
        return sb.toString();
    }

    private String buildStudentGapPrompt(User student, List<Job> candidates, Set<String> freeSlotsSet) {
        StringBuilder sb = new StringBuilder("你是校园兼职平台的智能匹配助手。\n"); for (String slot : freeSlotsSet) sb.append(slot).append(" ");
        for (Job j : candidates) sb.append("ID:").append(j.getId()).append(" 标题:").append(j.getTitle()).append("\n");
        sb.append("只输出{\"matches\":[{\"id\":ID,\"score\":整数}]}");
        return sb.toString();
    }

    private String formatTimePref(String timePref) {
        if (timePref == null || timePref.isEmpty()) return "未设置";
        try { return objectMapper.readValue(timePref, new TypeReference<List<Map<String, Object>>>() {}).stream().map(s -> getDayName(((Number) s.get("day")).intValue()) + (s.get("period") instanceof String ? s.get("period") : new String[]{"上午","下午","晚上"}[((Number) s.get("period")).intValue() - 1])).collect(Collectors.joining("、")); } catch (Exception e) { return "未设置"; }
    }

    private String formatJobTime(String timeJson) {
        if (timeJson == null) return "未设置";
        try { Map<String, Object> obj = objectMapper.readValue(timeJson, new TypeReference<>() {}); if (obj.containsKey("timeSlots")) { return ((List<Map<String, Object>>) obj.get("timeSlots")).stream().map(s -> getDayName(((Number) s.get("day")).intValue()) + ("morning".equals(s.get("period")) ? "上午" : "afternoon".equals(s.get("period")) ? "下午" : "晚上")).collect(Collectors.joining("、")); } } catch (Exception e) {}
        return "未设置";
    }

    private String formatTimeSlots(List<TimeSlotExtractor.TimeSlot> slots) { String[] dn = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"}; return slots.stream().map(ts -> { String d = ts.dayOfWeek != null && ts.dayOfWeek <= 7 ? dn[ts.dayOfWeek] : "?"; String s = ts.timeSlot != null ? ("morning".equals(ts.timeSlot) ? "上午" : "afternoon".equals(ts.timeSlot) ? "下午" : ts.timeSlot) : ""; return d + s; }).distinct().collect(Collectors.joining(" ")); }
    private int countUserTimeSlots(User user) { return 0; }



    public void processMatchingStream(String userInput, String userIdStr, SseEmitter emitter) {
        User currentUser = userService.getUserById(Integer.parseInt(userIdStr));
        String identity = currentUser != null ? currentUser.getIdentity() : "";
        List<TimeSlotExtractor.TimeSlot> timeSlots = TimeSlotExtractor.extract(userInput);
        Set<String> requiredSlotSet = timeSlotsToSet(timeSlots);
        String timeHint = "";

        String prompt;
        Map<Integer, Integer> ragScoreLookup = new HashMap<>();
        boolean preferDaily = userInput.contains("日结");
        boolean preferHighSalary = userInput.contains("高薪") || userInput.contains("高薪资");
        boolean preferCampus = userInput.contains("校园") || userInput.contains("学校附近") || userInput.contains("校内");

        if ("商户".equals(identity)) {
            List<User> rawStudents = timeSlots.isEmpty() ? getAvailableStudents() : filterStudentsBySlots(timeSlots);
            List<User> students = filterStudentsByRealAvailability(rawStudents, timeSlots);
            Map<User, Integer> ragScores = ragPreScoreStudents(students, requiredSlotSet);
            ragScores.forEach((user, score) -> ragScoreLookup.put(user.getId(), score));
            List<Map.Entry<User, Integer>> sortedByRag = ragScores.entrySet().stream()
                .sorted(Map.Entry.<User, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
            List<User> topCandidates = new ArrayList<>();
            for (int i = 0; i < Math.min(10, sortedByRag.size()); i++) topCandidates.add(sortedByRag.get(i).getKey());
            timeHint = timeSlots.isEmpty() ? "" : "已提取时间约束: " + formatTimeSlots(timeSlots);
            prompt = buildRagAgentPrompt(userInput, topCandidates, sortedByRag, requiredSlotSet, "student", identity, timeHint, currentUser);
        } else if ("学生".equals(identity)) {
            Set<String> realFreeSlots = cacheClearService.getStudentAvailableSlots(currentUser.getId());
            if (realFreeSlots.isEmpty()) {
                try { emitter.send(SseEmitter.event().name("final").data(Map.of("type", "final", "content", "您当前没有空闲时段"))); emitter.complete(); } catch (Exception e) {}
                return;
            }
            Set<Integer> jids = new LinkedHashSet<>();
            for (String k : realFreeSlots) { String[] p = k.split("_"); for (Job j : jobMapper.selectByDayAndSlot(Integer.parseInt(p[0]), p[1])) jids.add(j.getId()); }
            List<Job> jobs = jids.stream().map(jobMapper::selectById).filter(j -> j != null && "进行中".equals(j.getStatus())).filter(j -> !cacheClearService.getJobAvailableSlots(j.getId()).isEmpty()).collect(Collectors.toList());
            if (preferDaily) jobs = jobs.stream().filter(j -> "daily".equals(j.getRemunerationType())).collect(Collectors.toList());
            if (preferHighSalary) jobs.sort((a, b) -> { try { return Integer.compare(parseSalary(b.getSalary()), parseSalary(a.getSalary())); } catch (Exception e) { return 0; } });
            if (preferCampus) jobs = jobs.stream().filter(j -> j.getAddress() != null && (j.getAddress().contains("校") || j.getAddress().contains("校内"))).collect(Collectors.toList());
            Map<Job, Integer> ragScores_j = ragPreScoreJobs(jobs, realFreeSlots);
            ragScores_j.forEach((job, score) -> ragScoreLookup.put(job.getId(), score));
            List<Map.Entry<Job, Integer>> sortedByRag = ragScores_j.entrySet().stream()
                .sorted(Map.Entry.<Job, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
            List<Job> topCandidates = new ArrayList<>();
            for (int i = 0; i < Math.min(10, sortedByRag.size()); i++) topCandidates.add(sortedByRag.get(i).getKey());
            timeHint = "基于您的真实空闲时段自动匹配";
            prompt = buildRagAgentPrompt(userInput, topCandidates, sortedByRag, realFreeSlots, "job", identity, timeHint, currentUser);
        } else {
            List<User> rawStudents = timeSlots.isEmpty() ? getAvailableStudents() : filterStudentsBySlots(timeSlots);
            List<User> students = filterStudentsByRealAvailability(rawStudents, timeSlots);
            List<Job> rawJobs = timeSlots.isEmpty() ? getAvailableJobs() : filterJobsBySlots(timeSlots);
            Map<User, Integer> sScores = ragPreScoreStudents(students, requiredSlotSet);
            sScores.forEach((user, score) -> ragScoreLookup.put(user.getId(), score));
            List<Map.Entry<User, Integer>> sSorted = sScores.entrySet().stream().sorted(Map.Entry.<User, Integer>comparingByValue().reversed()).collect(Collectors.toList());
            List<User> topStu = new ArrayList<>(); for (int i = 0; i < Math.min(5, sSorted.size()); i++) topStu.add(sSorted.get(i).getKey());
            Map<Job, Integer> jScores = ragPreScoreJobs(rawJobs, requiredSlotSet);
            jScores.forEach((job, score) -> ragScoreLookup.put(job.getId(), score));
            List<Map.Entry<Job, Integer>> jSorted = jScores.entrySet().stream().sorted(Map.Entry.<Job, Integer>comparingByValue().reversed()).collect(Collectors.toList());
            List<Job> topJo = new ArrayList<>(); for (int i = 0; i < Math.min(5, jSorted.size()); i++) topJo.add(jSorted.get(i).getKey());
            timeHint = timeSlots.isEmpty() ? "" : "已提取时间约束: " + formatTimeSlots(timeSlots);
            prompt = buildRagAgentPrompt(userInput, topStu, sSorted, requiredSlotSet, "student", identity, timeHint, currentUser);
            if (!topJo.isEmpty()) { String jp = buildRagAgentPrompt(userInput, topJo, jSorted, requiredSlotSet, "job", identity, timeHint, currentUser); prompt = prompt + "\n---\n" + jp.substring(jp.indexOf("### 候选列表")); }
        }

        boolean aiSuccess = false; StringBuilder fullResponse = new StringBuilder(), thinkingBuffer = new StringBuilder();
        try {
            Map<String, Object> body = new LinkedHashMap<>(); body.put("model", "qwen2.5:3b"); body.put("prompt", prompt); body.put("stream", true); body.put("temperature", 0.0); body.put("num_predict", 256); body.put("num_ctx", 2048);
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:11434/api/generate")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).timeout(java.time.Duration.ofMinutes(3)).build(), HttpResponse.BodyHandlers.ofInputStream());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) { if (line.trim().isEmpty()) continue; Map<String, Object> chunk = objectMapper.readValue(line, new TypeReference<>() {}); String ct = (String) chunk.get("response"); if (ct == null) continue; fullResponse.append(ct); if (ct.contains("<think>")) thinkingBuffer.append(ct); if (thinkingBuffer.length() > 0) emitter.send(SseEmitter.event().name("thinking").data(thinkingBuffer.toString())); }
            }
            if (fullResponse.length() > 0) { String thinking = extractThinkingFromResponse(fullResponse.toString()); String cleaned = cleanAiResponse(removeThinkTags(fullResponse.toString())); List<AIMatchResult> matchResults = parseSimpleMatchesResponse(cleaned); if (matchResults != null && !matchResults.isEmpty()) { aiSuccess = true; String sid = UUID.randomUUID().toString(); List<Map<String, Object>> flat = new ArrayList<>(); for (AIMatchResult r : matchResults) { Map<String, Object> item = buildMatchItem(r); if (item != null) flat.add(item); } sanitizeMatchItems(flat, identity, currentUser, requiredSlotSet); 
                    for (Map<String, Object> m : flat) {
                        Integer mid = (Integer) m.get("id");
                        Integer rs = ragScoreLookup.get(mid);
                        if (rs != null) m.put("score", Math.max((Integer) m.get("score"), rs));
                    }
                    sessionStore.put(sid, new SessionContext(Integer.parseInt(userIdStr), identity, userInput, flat)); Map<String, Object> finalResult = new HashMap<>(); finalResult.put("type", "final"); finalResult.put("sessionId", sid); finalResult.put("matches", flat); finalResult.put("total", flat.size()); emitter.send(SseEmitter.event().name("final").data(finalResult)); } }
        } catch (Exception e) {}
        if (!aiSuccess) {
            List<Map<String, Object>> localFlat = "商户".equals(identity) ? buildLocalStudentMatches(timeSlots.isEmpty() ? getAvailableStudents() : filterStudentsBySlots(timeSlots), requiredSlotSet) : buildLocalJobMatches(getAvailableJobs(), cacheClearService.getStudentAvailableSlots(Integer.parseInt(userIdStr)));
            String sid = UUID.randomUUID().toString(); sessionStore.put(sid, new SessionContext(Integer.parseInt(userIdStr), identity, userInput, localFlat));
            try { emitter.send(SseEmitter.event().name("final").data(Map.of("type", "final", "sessionId", sid, "matches", localFlat, "total", localFlat.size(), "thinking", "AI未成功，使用本地匹配"))); } catch (Exception e) {}
        }
        emitter.complete();
    }



    public void clearStudentAvailableCache(int studentId) { studentAvailableCache.remove(studentId); }
    public void clearJobAvailableCache(int jobId) { jobAvailableCache.remove(jobId); }

    @Scheduled(fixedRate = 600000)
    public void cleanExpiredSessions() { long now = System.currentTimeMillis(), maxAge = 30 * 60 * 1000; sessionStore.entrySet().removeIf(e -> now - e.getValue().createdAt > maxAge); }



    private boolean isMatchingIntent(String input) { return input != null && !input.trim().isEmpty(); }
    private int timeSlotToPeriod(String timeSlot) { return "morning".equals(timeSlot) ? 1 : "afternoon".equals(timeSlot) ? 2 : 3; }
    private String periodToChinese(int period) { return period == 1 ? "上午" : period == 2 ? "下午" : "晚上"; }
    private List<Map<String, Object>> parseStudentFreeSlots(int studentId) { return Collections.emptyList(); }

    private String extractKeywordHints(String input) {
        List<String> hints = new ArrayList<>();
        if (input.contains("日结")) hints.add("日结优先");
        if (input.contains("周结") || input.contains("长工")) hints.add("周结优先");
        if (input.contains("高薪") || input.contains("高薪资")) hints.add("薪资从高到低");
        if (input.contains("校园") || input.contains("校内") || input.contains("学校附近")) hints.add("校园内优先");
        if (input.contains("线上") || input.contains("远程")) hints.add("线上优先");
        if (input.contains("新手") || input.contains("无需经验")) hints.add("新手友好");
        if (input.contains("短期")) hints.add("短期兼职优先");
        if (input.contains("周末")) hints.add("周末优先");
        return hints.isEmpty() ? "" : "关键词: " + String.join(", ", hints);
    }

    private int parseSalary(String salary) {
        if (salary == null) return 0;
        String s = salary.replaceAll("[^0-9]", "");
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }
    private String getDayName(int day) { switch (day) { case 1: return "周一"; case 2: return "周二"; case 3: return "周三"; case 4: return "周四"; case 5: return "周五"; case 6: return "周六"; case 7: return "周日"; default: return ""; } }
    private void cleanMatchResults(List<AIMatchResult> results) {}
    private String extractJson(String text) { int s = text.indexOf('{'), e = text.lastIndexOf('}'); return (s == -1 || e == -1 || e <= s) ? null : text.substring(s, e + 1); }
    private String fixScoreExpressions(String json) { return json; }
    private String extractFinalNumber(String expr) { return null; }
    private String extractFirstNumber(String expr) { return null; }



    static class SessionContext { final int userId; final String identity; final String query; final List<Map<String, Object>> matches; final long createdAt; SessionContext(int userId, String identity, String query, List<Map<String, Object>> matches) { this.userId = userId; this.identity = identity; this.query = query; this.matches = matches; this.createdAt = System.currentTimeMillis(); } }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AIMatchResult { private String type; private int id; private int score; private String reason; private List<Map<String, Object>> suggestedActions; public String getType() { return type; } public void setType(String type) { this.type = type; } public int getId() { return id; } public void setId(int id) { this.id = id; } public int getScore() { return score; } public void setScore(int score) { this.score = score; } public String getReason() { return reason; } public void setReason(String reason) { this.reason = reason; } public List<Map<String, Object>> getSuggestedActions() { return suggestedActions; } public void setSuggestedActions(List<Map<String, Object>> suggestedActions) { this.suggestedActions = suggestedActions; } }
}
