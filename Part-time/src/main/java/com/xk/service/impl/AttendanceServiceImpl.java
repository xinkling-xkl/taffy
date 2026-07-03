package com.xk.service.impl;

import com.xk.entity.Job;
import com.xk.mapper.AttendanceMapper;
import com.xk.mapper.JobApplicationMapper;
import com.xk.mapper.JobMapper;
import com.xk.mapper.WorkAssignmentMapper;
import com.xk.service.AttendanceService;
import com.xk.service.CacheClearService;
import com.xk.service.CreditService;
import com.xk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xk.mapper.AssignmentMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xk.utils.TypeConversionUtils.toInteger;

@Service
public class AttendanceServiceImpl implements AttendanceService {


    @Autowired
    private AttendanceMapper attendanceMapper;
    @Autowired
    private WorkAssignmentMapper workAssignmentMapper;
    @Autowired
    private CreditService creditService;
    @Autowired
    private UserService userService;
    @Autowired
    private JobMapper jobMapper;
    @Autowired
    private AssignmentMapper assignmentMapper;
    @Autowired
    private JobApplicationMapper jobApplicationMapper;
    @Autowired
    private MatchingAiServiceImpl matchingAiService;
    @Autowired
    private CacheClearService cacheClearService;



    @Override
    @Transactional
    public String checkIn(Integer workAssignmentId, Integer studentId, String checkInTime, Boolean isLate, Integer lateMinutes) {
        try {
            Map<String, Object> existingAttendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
            if (existingAttendance != null && existingAttendance.get("check_in_time") != null) {
                return "已经签到过了";
            }

            Map<String, Object> workAssignment = attendanceMapper.getWorkAssignmentById(workAssignmentId);
            if (workAssignment == null) return "工作分配不存在";

            Integer assignmentStudentId = toInteger(workAssignment.get("student_id"));
            if (!studentId.equals(assignmentStudentId)) return "学生ID不匹配";

            LocalDate workDate = ((java.sql.Date) workAssignment.get("work_date")).toLocalDate();
            if (!workDate.equals(LocalDate.now())) return "今天没有该工作安排";

            String waStatus = (String) workAssignment.get("status");
            if ("cancelled".equals(waStatus)) return "该时段已请假，无法签到";

            LocalDateTime now = LocalDateTime.now();
            String timeSlot = (String) workAssignment.get("time_slot");
            LocalDateTime workStartTime = getWorkStartTime(workDate, timeSlot);
            LocalDateTime workEndTime = getWorkEndTime(workDate, timeSlot);

            LocalDateTime allowedCheckInStart = workStartTime.minusMinutes(5);
            if (now.isBefore(allowedCheckInStart)) return "还未到签到时间";
            if (now.isAfter(workEndTime)) return "工作时间已过，无法签到";

            boolean actualIsLate = false;
            int actualLateMinutes = 0;
            if (now.isAfter(workStartTime)) {
                actualLateMinutes = (int) ChronoUnit.MINUTES.between(workStartTime, now);
                actualIsLate = actualLateMinutes > 5;
            }

            if (isLate == null) isLate = actualIsLate;
            if (lateMinutes == null || lateMinutes == 0) lateMinutes = actualLateMinutes;

            attendanceMapper.insertAttendance(workAssignmentId, studentId, now, isLate ? 1 : 0, lateMinutes);
            attendanceMapper.updateWorkAssignmentStatus(workAssignmentId, "checked_in");

            if (isLate) creditService.updateCreditScore(studentId, -8, "迟到", "late");
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "签到失败: " + e.getMessage();
        }
    }

    @Override
    @Transactional
    public String checkOut(Integer workAssignmentId, Integer studentId, String checkOutTime) {
        try {
            Map<String, Object> attendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
            if (attendance == null) return "未找到签到记录";
            if (attendance.get("check_out_time") != null) return "已经签退过了";

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime checkInTime = ((java.sql.Timestamp) attendance.get("check_in_time")).toLocalDateTime();
            long durationMinutes = ChronoUnit.MINUTES.between(checkInTime, now);

            attendanceMapper.updateCheckOutTime(workAssignmentId, now);
            attendanceMapper.updateWorkAssignmentStatus(workAssignmentId, "completed");

            int workHours = calculateWorkHours(durationMinutes);
            if (workHours > 0) creditService.updateCreditScore(studentId, workHours * 2, "完成工作", "complete_job");

            userService.updateWorkingStatus(studentId, 0);
            attendanceMapper.updateWorkDuration(workAssignmentId, (int) durationMinutes);

            releaseSlotAndRecoverJob(workAssignmentId);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "签退失败: " + e.getMessage();
        }
    }

    @Override
    public List<Map<String, Object>> getTodayAssignments(Integer studentId) {
        List<Map<String, Object>> assignments = attendanceMapper.getTodayAttendance(studentId);
        assignments.removeIf(a -> {
            String status = (String) a.get("status");
            return !"assigned".equals(status) && !"checked_in".equals(status)
                    && !"pending_confirmation".equals(status) && !"cancelled".equals(status);
        });

        for (Map<String, Object> assignment : assignments) {
            Integer workAssignmentId = toInteger(assignment.get("id"));
            Map<String, Object> att = workAssignmentId != null
                    ? attendanceMapper.getAttendanceByAssignmentId(workAssignmentId) : null;
            fillAttendanceDefaults(att);
            assignment.put("attendance", att);
        }
        return assignments;
    }

    @Override
    public List<Map<String, Object>> getHistoryAttendances(Integer studentId) {
        try {
            return attendanceMapper.getHistoryAttendance(studentId);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }



    @Override
    public boolean checkMerchantConfirm(Integer workAssignmentId) {
        try {
            Map<String, Object> workAssignment = attendanceMapper.getWorkAssignmentById(workAssignmentId);
            if (workAssignment == null) return false;
            Integer canCheckOut = toInteger(workAssignment.get("can_check_out"));
            return canCheckOut != null && canCheckOut == 1;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String merchantConfirm(Integer workAssignmentId, Integer merchantId) {
        try {
            if (!checkMerchantPermission(workAssignmentId, merchantId)) return "没有权限确认此工作分配";

            Map<String, Object> workAssignment = attendanceMapper.getWorkAssignmentById(workAssignmentId);
            if (workAssignment == null) return "工作分配不存在";

            Map<String, Object> attendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
            if (attendance == null || attendance.get("check_in_time") == null) return "学生尚未签到，无法确认";

            Integer canCheckOut = toInteger(workAssignment.get("can_check_out"));
            if (canCheckOut != null && canCheckOut == 1) return "已经确认过了";

            attendanceMapper.updateMerchantSettled(workAssignmentId, LocalDateTime.now());
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "确认失败: " + e.getMessage();
        }
    }

    @Override
    public List<Map<String, Object>> getMerchantTodayAssignments(Integer merchantId) {
        List<Map<String, Object>> assignments = workAssignmentMapper.getMerchantTodayAssignments(merchantId);
        for (Map<String, Object> assignment : assignments) {
            Integer workAssignmentId = toInteger(assignment.get("id"));
            Map<String, Object> attendance = workAssignmentId != null
                    ? attendanceMapper.getAttendanceByAssignmentId(workAssignmentId) : null;
            fillAttendanceDefaults(attendance);
            assignment.put("attendance", attendance);
        }
        return assignments;
    }

    @Override
    public List<Map<String, Object>> getMerchantHistoryAttendances(Integer merchantId) {
        List<Map<String, Object>> assignments = workAssignmentMapper.getMerchantHistoryAssignments(merchantId);
        for (Map<String, Object> assignment : assignments) {
            Integer workAssignmentId = toInteger(assignment.get("id"));
            if (workAssignmentId != null) {
                Map<String, Object> attendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
                if (attendance != null) {
                    Object attendanceStatus = attendance.get("attendance_status");
                    attendance.put("status", attendanceStatus != null ? attendanceStatus.toString() : "pending");
                    assignment.put("attendance", attendance);
                }
            }
        }
        return assignments;
    }

    @Override
    @Transactional
    public String markEarlyLeave(Integer workAssignmentId, Integer merchantId, String reason) {
        if (!checkMerchantPermission(workAssignmentId, merchantId)) return "没有权限操作此工作分配";
        try {
            Map<String, Object> attendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
            if (attendance == null) return "未找到考勤记录";

            LocalDateTime checkOutTime = LocalDateTime.now();
            attendanceMapper.updateCheckOutTime(workAssignmentId, checkOutTime);
            attendanceMapper.updateAttendanceStatus(workAssignmentId, "early_leave");
            attendanceMapper.updateWorkAssignmentStatus(workAssignmentId, "completed");

            Integer studentId = toInteger(attendance.get("student_id"));
            if (studentId != null && attendance.get("check_in_time") != null) {
                LocalDateTime checkInTime = ((java.sql.Timestamp) attendance.get("check_in_time")).toLocalDateTime();
                long durationMinutes = ChronoUnit.MINUTES.between(checkInTime, checkOutTime);
                calculateWorkHours(durationMinutes);
                userService.updateWorkingStatus(studentId, 0);
            }
            releaseSlotAndRecoverJob(workAssignmentId);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "标记提前下班失败: " + e.getMessage();
        }
    }

    @Override
    @Transactional
    public String markLate(Integer workAssignmentId, Integer merchantId, String reason) {
        return transitionStatus(workAssignmentId, merchantId, "late", reason);
    }

    @Override
    @Transactional
    public String markAbsent(Integer workAssignmentId, Integer merchantId, String reason) {
        return transitionStatus(workAssignmentId, merchantId, "absent", reason);
    }

    @Override
    @Transactional
    public String markCheckedIn(Integer workAssignmentId, Integer merchantId, String reason) {
        try {
            if (!checkMerchantPermission(workAssignmentId, merchantId)) return "没有权限操作此工作分配";

            Map<String, Object> workAssignment = attendanceMapper.getWorkAssignmentById(workAssignmentId);
            if (workAssignment == null) return "工作分配不存在";

            Map<String, Object> existingAttendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
            if (existingAttendance != null && existingAttendance.get("check_in_time") != null) {
                Object statusObj = existingAttendance.get("attendance_status");
                String currentStatus = statusObj != null ? statusObj.toString() : "";
                if ("leave".equals(currentStatus)) return "该学生已请假，无法修改状态";
                if ("late".equals(currentStatus) || "absent".equals(currentStatus)) {
                    Integer studentId = toInteger(existingAttendance.get("student_id"));
                    if (studentId != null) transitionCredit(studentId, currentStatus, "checked_in", "恢复为正常签到");
                    attendanceMapper.updateAttendanceStatus(workAssignmentId, "checked_in");
                    attendanceMapper.clearLateFields(workAssignmentId);
                    return "success";
                }
                return "已经签到过了";
            }

            LocalDate workDate = ((java.sql.Date) workAssignment.get("work_date")).toLocalDate();
            if (!workDate.equals(LocalDate.now())) return "今天没有该工作安排";

            String timeSlot = (String) workAssignment.get("time_slot");
            LocalDateTime workStartTime = getWorkStartTime(workDate, timeSlot);
            LocalDateTime now = LocalDateTime.now();

            if (now.isBefore(workStartTime.minusMinutes(5))) return "还未到签到时间";

            boolean isLate = false;
            int lateMinutes = 0;
            if (now.isAfter(workStartTime)) {
                lateMinutes = (int) ChronoUnit.MINUTES.between(workStartTime, now);
                isLate = lateMinutes > 5;
            }

            Integer studentId = toInteger(workAssignment.get("student_id"));
            attendanceMapper.insertAttendance(workAssignmentId, studentId, now, isLate ? 1 : 0, lateMinutes);
            attendanceMapper.updateWorkAssignmentStatus(workAssignmentId, "checked_in");

            if (isLate && studentId != null) creditService.updateCreditScore(studentId, -5, "商户标记迟到: " + reason, "late");
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "标记为签到失败: " + e.getMessage();
        }
    }

    @Override
    @Transactional
    public String markNormalCheckedIn(Integer workAssignmentId, Integer merchantId) {
        try {
            if (!checkMerchantPermission(workAssignmentId, merchantId)) return "没有权限操作此工作分配";

            Map<String, Object> attendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
            if (attendance == null) return "未找到考勤记录";

            Object statusObj = attendance.get("attendance_status");
            String currentStatus = statusObj != null ? statusObj.toString() : "";
            if ("leave".equals(currentStatus)) return "该学生已请假，无法修改状态";
            if (!"late".equals(currentStatus) && !"absent".equals(currentStatus)) return "当前状态无需恢复";

            Integer studentId = toInteger(attendance.get("student_id"));
            if (studentId == null) return "未找到学生信息";

            attendanceMapper.updateAttendanceStatus(workAssignmentId, "checked_in");
            attendanceMapper.clearLateFields(workAssignmentId);
            transitionCredit(studentId, currentStatus, "checked_in", "恢复为正常签到");
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "操作失败: " + e.getMessage();
        }
    }

    @Override
    @Transactional
    public String merchantCheckOut(Integer workAssignmentId, Integer merchantId, String checkOutTime) {
        try {
            if (!checkMerchantPermission(workAssignmentId, merchantId)) return "没有权限操作此工作分配";

            Map<String, Object> attendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
            if (attendance == null) return "未找到考勤记录";
            if (attendance.get("check_out_time") != null) return "已经签退过了";

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime checkInTime = ((java.sql.Timestamp) attendance.get("check_in_time")).toLocalDateTime();
            long durationMinutes = ChronoUnit.MINUTES.between(checkInTime, now);

            attendanceMapper.updateCheckOutTime(workAssignmentId, now);
            attendanceMapper.updateWorkAssignmentStatus(workAssignmentId, "completed");

            int workHours = calculateWorkHours(durationMinutes);
            Integer studentId = toInteger(attendance.get("student_id"));
            if (studentId != null && workHours > 0) creditService.updateCreditScore(studentId, workHours * 2, "完成工作", "complete_job");
            if (studentId != null) userService.updateWorkingStatus(studentId, 0);
            attendanceMapper.updateWorkDuration(workAssignmentId, (int) durationMinutes);

            releaseSlotAndRecoverJob(workAssignmentId);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "商户签退失败: " + e.getMessage();
        }
    }



    private static final Map<String, Integer> STATUS_CREDIT_DELTA = Map.of(
            "checked_in", 0, "late", -8, "absent", -15, "early_leave", -5, "leave", 0);

    private String transitionStatus(Integer workAssignmentId, Integer merchantId, String newStatus, String reason) {
        if (!checkMerchantPermission(workAssignmentId, merchantId)) return "没有权限操作此工作分配";
        try {
            Map<String, Object> attendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
            String oldStatus = "checked_in";
            if (attendance != null) {
                Object s = attendance.get("attendance_status");
                if (s != null) {
                    oldStatus = s.toString();
                    if ("leave".equals(oldStatus)) return "该学生已请假，无法修改状态";
                }
            }
            if (attendance == null) {
                Map<String, Object> wa = attendanceMapper.getWorkAssignmentById(workAssignmentId);
                if (wa == null) return "工作分配不存在";
                Integer sid = toInteger(wa.get("student_id"));
                attendanceMapper.insertAttendance(workAssignmentId, sid, null, 0, 0);
            }

            attendanceMapper.updateAttendanceStatus(workAssignmentId, newStatus);

            Integer studentId = toInteger(attendance != null ? attendance.get("student_id") : null);
            if (studentId == null) {
                Map<String, Object> wa = attendanceMapper.getWorkAssignmentById(workAssignmentId);
                studentId = wa != null ? toInteger(wa.get("student_id")) : null;
            }
            if (studentId != null) {
                String label = "late".equals(newStatus) ? "迟到" : "absent".equals(newStatus) ? "缺勤" : newStatus;
                transitionCredit(studentId, oldStatus, newStatus, label + ": " + reason);
            }
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "标记" + newStatus + "失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void transitionCredit(Integer studentId, String oldStatus, String newStatus, String reason) {
        if (studentId == null) return;
        int oldDelta = STATUS_CREDIT_DELTA.getOrDefault(oldStatus, 0);
        int newDelta = STATUS_CREDIT_DELTA.getOrDefault(newStatus, 0);
        int netChange = newDelta - oldDelta;
        if (netChange != 0) {
            creditService.updateCreditScore(studentId, netChange, reason + " (" + oldStatus + "→" + newStatus + ")", newStatus);
        }
    }



    private void releaseSlotAndRecoverJob(Integer workAssignmentId) {
        if (workAssignmentId == null) return;
        Map<String, Object> wa = attendanceMapper.getWorkAssignmentById(workAssignmentId);
        if (wa == null) return;
        Integer jobId = toInteger(wa.get("job_id"));
        LocalDate workDate = ((java.sql.Date) wa.get("work_date")).toLocalDate();
        String slot = (String) wa.get("time_slot");
        int dow = workDate.getDayOfWeek().getValue();
        jobMapper.updateMerchantRequirementFilledCount(jobId, dow, slot, -1);
        recoverJobStatusIfNeeded(jobId);
    }

    private void recoverJobStatusIfNeeded(Integer jobId) {
        if (jobId == null) return;
        Job job = jobMapper.selectById(jobId);
        if (job == null || !"已招满".equals(job.getStatus())) return;

        int limit = 0;
        if (job.getRecruitmentLimit() != null && !job.getRecruitmentLimit().isEmpty()) {
            try { limit = Integer.parseInt(job.getRecruitmentLimit().replaceAll("[^0-9]", "")); }
            catch (NumberFormatException ignored) {}
        }

        int acceptedCount = jobApplicationMapper.getAcceptedApplicationsCount(jobId);
        if (acceptedCount < limit) { jobMapper.updateJobStatus(jobId, "进行中"); return; }

        List<Map<String, Object>> reqs = assignmentMapper.getRequirementsByJobId(jobId);
        if (reqs != null) {
            boolean anyOpen = reqs.stream().anyMatch(r -> {
                int needed = Integer.parseInt(r.get("needed_count").toString());
                int filled = Integer.parseInt(r.get("filled_count").toString());
                return filled < needed;
            });
            if (anyOpen) jobMapper.updateJobStatus(jobId, "进行中");
        }
    }



    private boolean checkMerchantPermission(Integer workAssignmentId, Integer merchantId) {
        try {
            com.xk.entity.User operator = userService.getUserById(merchantId);
            if (operator != null && "管理员".equals(operator.getIdentity())) return true;

            Map<String, Object> workAssignment = attendanceMapper.getWorkAssignmentById(workAssignmentId);
            if (workAssignment == null) return false;

            Integer jobId = toInteger(workAssignment.get("job_id"));
            if (jobId == null) return false;

            Map<String, Object> jobInfo = attendanceMapper.getJobById(jobId);
            if (jobInfo == null) return false;

            Integer jobMerchantId = toInteger(jobInfo.get("userId"));
            return merchantId.equals(jobMerchantId);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private LocalDateTime getWorkStartTime(LocalDate workDate, String timeSlot) {
        LocalDateTime t = workDate.atStartOfDay();
        switch (timeSlot) {
            case "morning":  t = t.withHour(9).withMinute(0); break;
            case "afternoon": t = t.withHour(14).withMinute(0); break;
            case "evening":  t = t.withHour(18).withMinute(0); break;
            default:         t = t.withHour(9).withMinute(0); break;
        }
        return t.withSecond(0).withNano(0);
    }

    private LocalDateTime getWorkEndTime(LocalDate workDate, String timeSlot) {
        LocalDateTime t = workDate.atStartOfDay();
        switch (timeSlot) {
            case "morning":  t = t.withHour(13).withMinute(0); break;
            case "afternoon": t = t.withHour(18).withMinute(0); break;
            case "evening":  t = t.withHour(22).withMinute(0); break;
            default:         t = t.withHour(13).withMinute(0); break;
        }
        return t.withSecond(0).withNano(0);
    }

    private int calculateWorkHours(long minutes) {
        return minutes >= 45 ? (int) Math.ceil(minutes / 60.0) : 0;
    }

    private void fillAttendanceDefaults(Map<String, Object> att) {
        if (att == null) att = new HashMap<>();
        if (!att.containsKey("check_in_time")) att.put("check_in_time", null);
        if (!att.containsKey("check_out_time")) att.put("check_out_time", null);
        if (!att.containsKey("is_late")) att.put("is_late", false);
        if (!att.containsKey("late_minutes")) att.put("late_minutes", 0);
        if (!att.containsKey("status")) {
            Object s = att.get("attendance_status");
            att.put("status", s != null ? s.toString() : "pending");
        }
        if (!att.containsKey("attendance_status")) att.put("attendance_status", "pending");
        if (!att.containsKey("is_early_leave")) att.put("is_early_leave", false);
        if (!att.containsKey("is_absent")) att.put("is_absent", false);
    }
}
