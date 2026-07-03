package com.xk.service.impl;

import com.xk.entity.User;
import com.xk.mapper.AssignmentMapper;
import com.xk.mapper.JobMapper;
import com.xk.service.AssignmentService;
import com.xk.service.CacheClearService;
import com.xk.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xk.util.DateCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AssignmentServiceImpl implements AssignmentService {

    @Autowired
    private AssignmentMapper assignmentMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private JobMapper jobMapper;
    @Autowired
    private CacheClearService cacheClearService;
    
    @Autowired
    private MatchingAiServiceImpl matchingAiService;
    @Override
    public List<Map<String, Object>> getStudentAssignments(Integer studentId) {
        return assignmentMapper.getStudentAssignments(studentId);
    }

    @Override
    public List<Map<String, Object>> getMerchantAssignments(Integer merchantId) {
        return assignmentMapper.getMerchantAssignments(merchantId);
    }

    @Override
    public String confirmAssignment(Integer assignmentId) {
        Map<String, Object> assignment = assignmentMapper.getAssignmentById(assignmentId);
        if (assignment == null) return "工作分配不存在";
        String status = (String) assignment.get("status");
        if (!"pending".equals(status)) return "该工作分配已确认或已取消";

        assignmentMapper.updateAssignmentStatus(assignmentId, "assigned");

        Integer studentId = toInteger(assignment.get("student_id"));
        String timeSlot = (String) assignment.get("time_slot");
        java.sql.Date sqlDate = (java.sql.Date) assignment.get("work_date");
        LocalDate workDate = sqlDate.toLocalDate();
        int dayOfWeek = workDate.getDayOfWeek().getValue();
        assignmentMapper.updateStudentSchedule(studentId, dayOfWeek, timeSlot, 0);

        Integer jobId = toInteger(assignment.get("job_id"));
        if (jobId != null) {
            checkAndUpdateJobStatus(jobId);
        }


                
return "success";
    }

    @Override
    public String inviteStudent(Integer studentId, Integer jobId, Integer merchantRequirementId,
                                String timeSlot, Integer dayOfWeek) {
        Map<String, Object> requirement = assignmentMapper.getRequirementById(merchantRequirementId);
        if (requirement == null) return "商户需求不存在";

        User student = userService.getUserById(studentId);
        if (student == null) return "学生不存在";
        if (isTimeConflict(student.getTimepreference(), dayOfWeek, timeSlot)) {
            return "该学生在此时段不可用";
        }

        LocalDate workDate = calculateWorkDate(dayOfWeek, timeSlot);
        assignmentMapper.insertAssignment(studentId, jobId, merchantRequirementId, workDate, timeSlot, "pending", LocalDateTime.now());

        // 邀请时更新需求人数
        jobMapper.updateMerchantRequirementFilledCount(jobId, dayOfWeek, timeSlot, 1);
        // 检查是否招满
        checkAndUpdateJobStatus(jobId);


                
return "success";
    }

    @Override
    public void createWorkAssignment(Integer studentId, Integer jobId, Integer merchantRequirementId,
                                     LocalDate workDate, String timeSlot, String status) {
        assignmentMapper.insertAssignment(studentId, jobId, merchantRequirementId, workDate, timeSlot, status, LocalDateTime.now());
        int dayOfWeek = workDate.getDayOfWeek().getValue();
        assignmentMapper.updateStudentSchedule(studentId, dayOfWeek, timeSlot, 0);
    }

    /**
     * 检查兼职的所有需求是否都已满人，如满则更新状态为"已招满"
     */
    private void checkAndUpdateJobStatus(Integer jobId) {
        try {
            System.out.println("========== [AssignmentService] 开始检查兼职ID: " + jobId + " 是否招满 ==========");
            List<Map<String, Object>> requirements = assignmentMapper.getRequirementsByJobId(jobId);
            if (requirements == null || requirements.isEmpty()) {
                System.out.println("兼职ID " + jobId + ": 没有找到商户需求记录(merchant_requirement)，无法判断是否招满");
                return;
            }

            System.out.println("找到 " + requirements.size() + " 个商户需求记录:");
            boolean allFilled = true;
            for (Map<String, Object> req : requirements) {
                Integer neededCount = req.get("needed_count") != null ?
                        Integer.parseInt(req.get("needed_count").toString()) : 0;
                Integer filledCount = req.get("filled_count") != null ?
                        Integer.parseInt(req.get("filled_count").toString()) : 0;
                System.out.println("  需求 - 需要: " + neededCount + " 人, 已招: " + filledCount + " 人");
                if (filledCount < neededCount) {
                    allFilled = false;
                }
            }

            if (allFilled) {
                System.out.println("兼职ID " + jobId + ": 所有需求都已满，更新状态为'已招满'");
                jobMapper.updateJobStatus(jobId, "已招满");
            } else {
                System.out.println("兼职ID " + jobId + ": 尚未招满，保持原状态");
            }
            System.out.println("========== [AssignmentService] 检查完成 ==========");
        } catch (Exception e) {
            System.err.println("检查兼职是否招满时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isTimeConflict(String timepref, int dayOfWeek, String timeSlot) {
        if (timepref == null || timepref.isEmpty()) return true;
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> slots = mapper.readValue(timepref,
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            int slotNum = 0;
            if ("morning".equals(timeSlot)) slotNum = 1;
            else if ("afternoon".equals(timeSlot)) slotNum = 2;
            else if ("evening".equals(timeSlot)) slotNum = 3;
            for (Map<String, Object> slot : slots) {
                int day = toInt(slot.get("day"));
                int period = toInt(slot.get("period"));
                if (day == dayOfWeek && slotNum == period) return false;
            }
        } catch (Exception ignored) {}
        return true;
    }

    private LocalDate calculateWorkDate(int targetDayOfWeek, String timeSlot) {
        return DateCalculator.getNextWorkDate(targetDayOfWeek, timeSlot, LocalDate.now());
    }

    private Integer toInteger(Object obj) {
        return com.xk.utils.TypeConversionUtils.toInteger(obj);
    }

    private int toInt(Object obj) {
        return com.xk.utils.TypeConversionUtils.toInt(obj);
    }
}
