package com.xk.service.impl;

import com.xk.dto.JobApplicationDTO;
import com.xk.entity.Job;
import com.xk.entity.JobApplication;
import com.xk.entity.User;
import com.xk.mapper.*;
import com.xk.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {


    @Autowired
    private JobApplicationMapper jobApplicationMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private ScheduleMapper scheduleMapper;
    @Autowired
    private JobMapper jobMapper;
    @Autowired
    private AssignmentMapper assignmentMapper;
    @Autowired
    private CreditService creditService;
    @Autowired
    private WorkAssignmentMapper workAssignmentMapper;
    @Autowired
    private MatchingAiServiceImpl matchingAiService;
    @Autowired
    private CacheClearService cacheClearService;



    @Override
    public String createApplication(JobApplication application) {
        try {
            User student = userService.getUserById(application.getStudentId());
            if (student == null) return "学生不存在";

            if (!creditService.checkCreditPermission(application.getStudentId()))
                return "您的信用分低于50，无法申请兼职。请通过完成工作或申诉恢复信用分。";

            Job job = jobMapper.selectById(application.getJobId());
            if (job == null) return "兼职不存在";
            if ("已招满".equals(job.getStatus())) return "该兼职已招满";

            JobApplication existing = jobApplicationMapper.getApplicationByJobAndStudent(
                    application.getJobId(), application.getStudentId());
            if (existing != null && !"rejected".equals(existing.getStatus()) && !"accepted".equals(existing.getStatus()))
                return "您已经申请过该兼职";

            if (existing != null && ("rejected".equals(existing.getStatus()) || "accepted".equals(existing.getStatus()))) {
                if ("accepted".equals(existing.getStatus())) {
                    List<Map<String, Object>> activeAssignments = workAssignmentMapper
                            .getActiveAssignmentsByJobIdAndStudent(application.getJobId(), application.getStudentId());
                    if (activeAssignments != null && !activeAssignments.isEmpty())
                        return "您当前仍在该兼职工作中，无法重复申请";
                }
                existing.setStatus("pending");
                existing.setTimeAvailability(application.getTimeAvailability());
                existing.setApplicationContent(application.getApplicationContent());
                existing.setSelectedDates(application.getSelectedDates());
                existing.setUpdatedAt(LocalDateTime.now());
                int updateResult = jobApplicationMapper.updateApplication(existing);
                if (updateResult > 0) { application.setId(existing.getId()); return "success"; }
                return "重新申请失败";
            }

            if (student.getTimepreference() == null || student.getTimepreference().trim().isEmpty())
                return "请先在个人中心设置您的空闲时间";

            if ("daily".equals(job.getRemunerationType())) {
                String selectedDatesJson = application.getSelectedDates();
                if (selectedDatesJson == null || selectedDatesJson.isEmpty()) return "请选择工作日期";
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    List<Map<String, Object>> dates = mapper.readValue(selectedDatesJson,
                            new TypeReference<List<Map<String, Object>>>() {});
                    LocalDate today = LocalDate.now();
                    LocalDate weekStart = today.with(DayOfWeek.MONDAY);
                    LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
                    int currentHour = LocalDateTime.now().getHour();
                    int currentMinute = LocalDateTime.now().getMinute();
                    Map<String, Integer> slotStartHour = Map.of("morning", 9, "afternoon", 14, "evening", 18);
                    for (Map<String, Object> d : dates) {
                        LocalDate date = LocalDate.parse((String) d.get("date"));
                        if (date.isBefore(weekStart) || date.isAfter(weekEnd)) return "只能选择本周内的日期";
                        if (date.isBefore(today)) return "不能选择已过去的日期";
                        if (date.isEqual(today)) {
                            String timeSlot = (String) d.get("timeSlot");
                            Integer startHour = slotStartHour.get(timeSlot);
                            if (startHour != null && (currentHour > startHour || (currentHour == startHour && currentMinute >= 0)))
                                return "该时间段已开始，请选择后续时间段";
                        }
                    }
                } catch (Exception e) { return "日期格式错误"; }
            }

            if (!"daily".equals(job.getRemunerationType())) {
                String conflict = checkTimeConflictWithRemaining(student, job);
                if (conflict != null) return conflict;
            }

            application.setStudentName(student.getName());
            application.setCreditScore(student.getCredit() != null ? student.getCredit() : 60);
            application.setStatus("pending");

            int result = jobApplicationMapper.insertApplication(application);
            return result > 0 ? "success" : "申请失败";
        } catch (Exception e) {
            e.printStackTrace();
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return "申请失败: " + msg;
        }
    }

    @Override
    public String updateApplicationStatus(int id, String status) {
        return updateApplicationStatus(id, status, null);
    }

    @Override
    public String updateApplicationStatus(int id, String status, String message) {
        return updateApplicationStatus(id, status, message, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateApplicationStatus(int id, String status, String message, boolean forceAccept) {
        try {
            JobApplication application = jobApplicationMapper.getApplicationById(id);
            if (application == null) return "申请不存在";
            if (!"pending".equals(application.getStatus())) return "该申请已被处理";
            if (!"accepted".equals(status) && !"rejected".equals(status)) return "无效的状态";

            int studentId = application.getStudentId();
            Job job = jobMapper.selectById(application.getJobId());
            if (job == null) return "兼职信息不存在";

            if ("accepted".equals(status) && !forceAccept) {
                User student = userService.getUserById(studentId);
                if (student != null && !"daily".equals(job.getRemunerationType())) {
                    String conflict = checkTimeConflictWithRemaining(student, job);
                    if (conflict != null) return "TIME_CONFLICT_PREFERENCE:" + conflict.replace("TIME_CONFLICT:", "");
                }
            }

            if ("accepted".equals(status)) {
                int totalAccepted = jobApplicationMapper.getAcceptedApplicationsCount(job.getId());
                int limit = parseRecruitmentLimit(job.getRecruitmentLimit());
                if (limit > 0 && totalAccepted >= limit) { jobMapper.updateJobStatus(job.getId(), "已招满"); return "该兼职已招满"; }
            }

            int result = jobApplicationMapper.updateApplicationStatus(id, status, message);

            if (result > 0 && "rejected".equals(status)) {
                releaseOccupiedSlots(application);
                cacheClearService.clearStudentCache(studentId);
                cacheClearService.clearJobCache(job.getId());
                recoverJobStatusIfNeeded(job.getId());
                return "success";
            }

            if (result > 0 && "accepted".equals(status)) {
                User student = userService.getUserById(studentId);
                if ("daily".equals(job.getRemunerationType()) || "hourly".equals(job.getRemunerationType())) {
                    List<Map<String, Object>> dateSlots = parseSelectedDates(application.getSelectedDates());
                    if (dateSlots != null && !dateSlots.isEmpty()) {
                        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
                        LocalDate weekEnd = LocalDate.now().with(DayOfWeek.SUNDAY);
                        for (Map<String, Object> slot : dateSlots) {
                            String dateStr = (String) slot.get("date");
                            String timeSlot = (String) slot.get("timeSlot");
                            if (dateStr == null || timeSlot == null) continue;
                            LocalDate workDate = LocalDate.parse(dateStr);
                            if (workDate.isBefore(weekStart) || workDate.isAfter(weekEnd)) continue;
                            workAssignmentMapper.createWorkAssignment(studentId, job.getId(), job.getUserId(), workDate.toString(), timeSlot, "assigned");
                            int dayOfWeek = workDate.getDayOfWeek().getValue();
                            jobMapper.updateMerchantRequirementFilledCount(job.getId(), dayOfWeek, timeSlot, 1);
                            assignmentMapper.updateStudentSchedule(studentId, dayOfWeek, timeSlot, 0);
                        }
                    } else {
                        createMatchedWorkAssignments(studentId, job, student, 1);
                    }
                    if (student != null) userService.updateWorkingStatus(studentId, 1);
                } else {
                    if (student != null && job.getTime() != null) createMatchedWorkAssignments(studentId, job, student);
                    if (student != null) userService.updateWorkingStatus(studentId, 1);
                }
                checkAndUpdateJobStatus(job.getId());
                recoverJobStatusIfNeeded(job.getId());
                cacheClearService.clearStudentCache(studentId);
                cacheClearService.clearJobCache(job.getId());
                return "success";
            }
            return result > 0 ? "success" : "更新失败";
        } catch (Exception e) {
            e.printStackTrace();
            return "更新失败: " + e.getMessage();
        }
    }

    @Override
    public List<JobApplicationDTO> getApplicationsByJobId(int jobId) { return convertToDTOList(jobApplicationMapper.getApplicationsByJobId(jobId)); }

    @Override
    public List<JobApplicationDTO> getApplicationsByStudentId(int studentId) { return convertToDTOList(jobApplicationMapper.getApplicationsByStudentId(studentId)); }

    @Override
    public List<JobApplicationDTO> getApplicationsByMerchantId(int merchantId) { return convertToDTOList(jobApplicationMapper.getApplicationsByMerchantId(merchantId)); }

    @Override
    public JobApplicationDTO getApplicationById(int id) { return convertToDTO(jobApplicationMapper.getApplicationById(id)); }

    @Override
    public int getAcceptedApplicationsCount(int jobId) {
        try { return jobApplicationMapper.getAcceptedApplicationsCount(jobId); }
        catch (Exception e) { e.printStackTrace(); return 0; }
    }

    @Override
    public boolean hasAcceptedApplication(int jobId, int studentId) {
        try {
            JobApplication app = jobApplicationMapper.getApplicationByJobAndStudent(jobId, studentId);
            return app != null && "accepted".equals(app.getStatus());
        } catch (Exception e) { e.printStackTrace(); return false; }
    }



    private void createMatchedWorkAssignments(int studentId, Job job, User student) { createMatchedWorkAssignments(studentId, job, student, 1); }

    private void createMatchedWorkAssignments(int studentId, Job job, User student, int weeksToCreate) {
        try {
            String remunerationType = job.getRemunerationType();
            if (remunerationType == null) remunerationType = "weekly";
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> timeObj = mapper.readValue(job.getTime(), Map.class);
            List<Map<String, Object>> requiredSlots = extractRequiredSlots(timeObj);
            List<Map<String, Object>> studentSlots = parseStudentSlots(student.getTimepreference());
            List<Map<String, Object>> remainingSlots = getRemainingSlots(job);

            LocalDate today = LocalDate.now();
            int currentDay = today.getDayOfWeek().getValue();
            int currentHour = LocalDateTime.now().getHour();
            int currentMinute = LocalDateTime.now().getMinute();
            Map<String, Integer> slotStartHour = Map.of("morning", 9, "afternoon", 14, "evening", 18);
            requiredSlots = requiredSlots.stream().filter(s -> {
                int day = (Integer) s.get("dayOfWeek");
                String ts = (String) s.get("timeSlot");
                if (day < currentDay) return false;
                if (day == currentDay) {
                    Integer startH = slotStartHour.get(ts);
                    if (startH != null && (currentHour > startH || (currentHour == startH && currentMinute >= 0))) return false;
                }
                return true;
            }).collect(Collectors.toList());

            if (requiredSlots.isEmpty()) return;
            if (createWeeklyAssignments(studentId, job, student, requiredSlots, studentSlots, remainingSlots, today, weeksToCreate))
                userService.updateWorkingStatus(studentId, 1);
            checkAndUpdateJobStatus(job.getId());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private boolean createWeeklyAssignments(int studentId, Job job, User student,
            List<Map<String, Object>> requiredSlots, List<Map<String, Object>> studentSlots,
            List<Map<String, Object>> remainingSlots, LocalDate today, int weeksToCreate) {
        boolean anyCreated = false;
        for (int week = 0; week < weeksToCreate; week++) {
            for (Map<String, Object> req : requiredSlots) {
                int dayOfWeek = (Integer) req.get("dayOfWeek");
                String slot = (String) req.get("timeSlot");
                boolean isSlotAvailable = false;
                for (Map<String, Object> r : remainingSlots) {
                    if (((Integer) r.get("dayOfWeek")) == dayOfWeek && slot.equals(r.get("timeSlot"))) { isSlotAvailable = true; break; }
                }
                if (!isSlotAvailable) continue;
                boolean isStudentFree = false;
                if (studentSlots != null) {
                    for (Map<String, Object> s : studentSlots) {
                        if (((Integer) s.get("day")) == dayOfWeek && periodNumToSlot((Integer) s.get("period")).equals(slot)) { isStudentFree = true; break; }
                    }
                }
                if (isStudentFree) {
                    LocalDate workDate = today.plusDays(calculateDaysUntil(dayOfWeek) + (week * 7L));
                    createWorkAssignment(studentId, job.getId(), job.getUserId(), workDate, slot, "assigned");
                    updateSlotFilledCount(job.getId(), dayOfWeek, slot, 1);
                    assignmentMapper.updateStudentSchedule(studentId, dayOfWeek, slot, 0);
                    anyCreated = true;
                }
            }
        }
        return anyCreated;
    }

    private void createWorkAssignment(int studentId, int jobId, int merchantId, LocalDate workDate, String timeSlot, String status) {
        try { workAssignmentMapper.createWorkAssignment(studentId, jobId, merchantId, workDate.toString(), timeSlot, status); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void updateSlotFilledCount(int jobId, int dayOfWeek, String timeSlot, int increment) {
        try { jobMapper.updateMerchantRequirementFilledCount(jobId, dayOfWeek, timeSlot, increment); }
        catch (Exception e) { e.printStackTrace(); }
    }



    private String checkTimeConflictWithRemaining(User student, Job job) {
        try {
            List<Map<String, Object>> remainingSlots = getRemainingSlots(job);
            if (remainingSlots.isEmpty()) return "该兼职已招满";
            List<Map<String, Object>> studentSlots = parseStudentSlots(student.getTimepreference());
            if (studentSlots == null) return "时间偏好格式异常，请重新设置";

            int matchedCount = 0;
            List<String> matchedSlots = new ArrayList<>(), unmatchedSlots = new ArrayList<>();
            for (Map<String, Object> req : remainingSlots) {
                int reqDay = (Integer) req.get("dayOfWeek");
                String reqSlot = (String) req.get("timeSlot");
                boolean found = false;
                for (Map<String, Object> stu : studentSlots) {
                    if (((Integer) stu.get("day")) == reqDay && periodNumToSlot((Integer) stu.get("period")).equals(reqSlot)) { found = true; matchedCount++; matchedSlots.add(getDayName(reqDay) + getTimeSlotChinese(reqSlot)); break; }
                }
                if (!found) unmatchedSlots.add(getDayName(reqDay) + getTimeSlotChinese(reqSlot));
            }
            if (matchedCount >= 2) return null;
            return "TIME_CONFLICT:仅匹配" + matchedCount + "个时段，需要至少匹配2个时段。";
        } catch (Exception e) { e.printStackTrace(); return "时间数据解析失败"; }
    }

    private List<Map<String, Object>> getRemainingSlots(Job job) {
        List<Map<String, Object>> remainingSlots = new ArrayList<>();
        try {
            String timeJson = job.getTime();
            if (timeJson == null || timeJson.isEmpty()) return remainingSlots;
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> timeObj = mapper.readValue(timeJson, Map.class);
            List<Map<String, Object>> requiredSlots = extractRequiredSlots(timeObj);
            List<Map<String, Object>> requirements = assignmentMapper.getRequirementsByJobId(job.getId());
            for (Map<String, Object> req : requiredSlots) {
                int dayOfWeek = (Integer) req.get("dayOfWeek");
                String timeSlot = (String) req.get("timeSlot");
                boolean isFull = false;
                if (requirements != null) {
                    for (Map<String, Object> r : requirements) {
                        if (Integer.parseInt(r.get("day_of_week").toString()) == dayOfWeek && timeSlot.equals(r.get("time_slot"))) {
                            if (Integer.parseInt(r.get("filled_count").toString()) >= Integer.parseInt(r.get("needed_count").toString())) isFull = true;
                            break;
                        }
                    }
                }
                if (!isFull) {
                    Map<String, Object> remaining = new HashMap<>();
                    remaining.put("dayOfWeek", dayOfWeek); remaining.put("timeSlot", timeSlot);
                    remainingSlots.add(remaining);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return remainingSlots;
    }

    private List<Map<String, Object>> extractRequiredSlots(Map<String, Object> timeObj) {
        List<Map<String, Object>> slots = new ArrayList<>();
        if (timeObj.containsKey("timeSlots") && timeObj.get("timeSlots") instanceof List) {
            for (Object obj : (List<?>) timeObj.get("timeSlots")) {
                Map<String, Object> slot = (Map<String, Object>) obj;
                Map<String, Object> req = new HashMap<>();
                req.put("dayOfWeek", slot.get("day")); req.put("timeSlot", slot.get("period"));
                slots.add(req);
            }
        }
        if (slots.isEmpty() && timeObj.containsKey("weekdays")) {
            String type = (String) timeObj.get("type");
            List<String> weekdays = (List<String>) timeObj.get("weekdays");
            if (weekdays != null && type != null) {
                for (String wd : weekdays) {
                    int day = getDayOfWeekNumber(wd);
                    for (String s : convertTypeToSlots(type)) {
                        Map<String, Object> req = new HashMap<>(); req.put("dayOfWeek", day); req.put("timeSlot", s); slots.add(req);
                    }
                }
            }
        }
        return slots;
    }



    private void checkAndUpdateJobStatus(Integer jobId) {
        try {
            Job job = jobMapper.selectById(jobId);
            if (job == null) return;
            int limit = parseRecruitmentLimit(job.getRecruitmentLimit());
            int totalAccepted = jobApplicationMapper.getAcceptedApplicationsCount(jobId);
            if (limit > 0 && totalAccepted >= limit) { jobMapper.updateJobStatus(jobId, "已招满"); return; }
            List<Map<String, Object>> requirements = assignmentMapper.getRequirementsByJobId(jobId);
            if (requirements == null || requirements.isEmpty()) return;
            boolean allFilled = requirements.stream().allMatch(r ->
                Integer.parseInt(r.get("filled_count").toString()) >= Integer.parseInt(r.get("needed_count").toString()));
            if (allFilled) jobMapper.updateJobStatus(jobId, "已招满");
            else if (!"进行中".equals(job.getStatus())) jobMapper.updateJobStatus(jobId, "进行中");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void recoverJobStatusIfNeeded(int jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null || !"已招满".equals(job.getStatus())) return;
        int limit = parseRecruitmentLimit(job.getRecruitmentLimit());
        if (jobApplicationMapper.getAcceptedApplicationsCount(jobId) < limit) { jobMapper.updateJobStatus(jobId, "进行中"); return; }
        List<Map<String, Object>> reqs = assignmentMapper.getRequirementsByJobId(jobId);
        if (reqs != null && reqs.stream().anyMatch(r ->
            Integer.parseInt(r.get("filled_count").toString()) < Integer.parseInt(r.get("needed_count").toString())))
            jobMapper.updateJobStatus(jobId, "进行中");
    }

    private int parseRecruitmentLimit(String limit) {
        if (limit == null || limit.isEmpty()) return 0;
        try { return Integer.parseInt(limit.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }



    private void releaseOccupiedSlots(JobApplication application) {
        Job job = jobMapper.selectById(application.getJobId());
        if (job == null) return;
        if ("daily".equals(job.getRemunerationType())) {
            for (Map<String, Object> slot : parseSelectedDates(application.getSelectedDates())) {
                jobMapper.updateMerchantRequirementFilledCount(job.getId(),
                    LocalDate.parse((String) slot.get("date")).getDayOfWeek().getValue(), (String) slot.get("timeSlot"), -1);
            }
        } else {
            User student = userService.getUserById(application.getStudentId());
            if (student != null && job.getTime() != null) {
                List<Map<String, Object>> requiredSlots = extractRequiredSlots(parseTimeObj(job.getTime()));
                List<Map<String, Object>> studentSlots = parseStudentSlots(student.getTimepreference());
                for (Map<String, Object> req : requiredSlots) {
                    int day = toInt(req.get("dayOfWeek")); String slot = (String) req.get("timeSlot");
                    if (studentSlots != null && studentSlots.stream().anyMatch(s ->
                        toInt(s.get("day")) == day && periodNumToSlot(toInt(s.get("period"))).equals(slot)))
                        jobMapper.updateMerchantRequirementFilledCount(job.getId(), day, slot, -1);
                }
            }
        }
        recoverJobStatusIfNeeded(job.getId());
    }

    private List<Map<String, Object>> parseSelectedDates(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try { return new ObjectMapper().readValue(json, new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); }
    }

    private Map<String, Object> parseTimeObj(String json) {
        try { return new ObjectMapper().readValue(json, Map.class); }
        catch (Exception e) { return new HashMap<>(); }
    }



    private int calculateDaysUntil(int targetDay) {
        int todayDow = LocalDate.now().getDayOfWeek().getValue();
        if (targetDay == todayDow) return 0;
        int diff = targetDay - todayDow;
        if (diff <= 0) diff += 7;
        return diff;
    }

    private List<Map<String, Object>> parseStudentSlots(String timepref) {
        try { return new ObjectMapper().readValue(timepref, new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception e) { return null; }
    }

    private String periodNumToSlot(int periodNum) {
        return periodNum == 1 ? "morning" : periodNum == 2 ? "afternoon" : "evening";
    }

    private String getDayName(int day) {
        switch (day) { case 1: return "周一"; case 2: return "周二"; case 3: return "周三"; case 4: return "周四"; case 5: return "周五"; case 6: return "周六"; case 7: return "周日"; default: return "未知"; }
    }

    private String getTimeSlotChinese(String slot) {
        return "morning".equals(slot) ? "上午" : "afternoon".equals(slot) ? "下午" : "evening".equals(slot) ? "晚上" : slot;
    }

    private int getDayOfWeekNumber(String weekday) {
        switch (weekday) { case "周一": return 1; case "周二": return 2; case "周三": return 3; case "周四": return 4; case "周五": return 5; case "周六": return 6; case "周日": return 7; default: return 0; }
    }

    private List<String> convertTypeToSlots(String type) {
        List<String> list = new ArrayList<>();
        if (type == null) return list;
        if (type.contains("白天") || type.contains("全天")) { list.add("morning"); list.add("afternoon"); }
        if (type.contains("夜晚") || type.contains("全天")) list.add("evening");
        return list;
    }

    private Integer toInteger(Object obj) { return com.xk.utils.TypeConversionUtils.toInteger(obj); }
    private int toInt(Object obj) { return com.xk.utils.TypeConversionUtils.toInt(obj); }

    private JobApplicationDTO convertToDTO(JobApplication app) {
        if (app == null) return null;
        JobApplicationDTO dto = new JobApplicationDTO();
        dto.setId(app.getId()); dto.setJobId(app.getJobId()); dto.setStudentId(app.getStudentId()); dto.setMerchantId(app.getMerchantId());
        dto.setStudentName(app.getStudentName()); dto.setTimeAvailability(app.getTimeAvailability()); dto.setCreditScore(app.getCreditScore());
        dto.setApplicationContent(app.getApplicationContent()); dto.setStatus(app.getStatus());
        dto.setCreatedAt(app.getCreatedAt()); dto.setUpdatedAt(app.getUpdatedAt()); dto.setMessage(app.getMessage()); dto.setSelectedDates(app.getSelectedDates());
        User merchant = userService.getUserById(app.getMerchantId()); if (merchant != null) dto.setMerchantName(merchant.getName());
        Job job = jobMapper.selectById(app.getJobId()); if (job != null) dto.setJobTitle(job.getTitle());
        return dto;
    }

    private List<JobApplicationDTO> convertToDTOList(List<JobApplication> apps) {
        List<JobApplicationDTO> dtos = new ArrayList<>();
        if (apps != null) for (JobApplication app : apps) dtos.add(convertToDTO(app));
        return dtos;
    }
}
