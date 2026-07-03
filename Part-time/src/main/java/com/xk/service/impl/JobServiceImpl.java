package com.xk.service.impl;

import com.xk.dto.JobApplicationDTO;
import com.xk.dto.WorkDTO;
import com.xk.entity.*;
import com.xk.exception.BusinessException;
import com.xk.mapper.*;
import com.xk.service.*;
import com.xk.utils.TypeConversionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.transaction.annotation.Transactional;

import static com.xk.utils.TypeConversionUtils.toInteger;

@Service
public class JobServiceImpl implements JobService {

    @Autowired
    private JobMapper jobMapper;

    @Autowired
    private JobApplicationMapper jobApplicationMapper;   // 改为 Mapper

    @Autowired
    private AssignmentMapper assignmentMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private WorkAssignmentMapper workAssignmentMapper;

    @Autowired
    private EvaluationMapper evaluationMapper;

    @Autowired
    private MessageService messageService;

    @Autowired
    private CreditService creditService;

    @Autowired
    private JobApplicationService jobApplicationService;


    @Autowired
    private CacheClearService cacheClearService;

    @Autowired
    private AttendanceMapper attendanceMapper;
    @Override
    public int addWork(Job work) {
        work.setStatus("进行中");
        work.setCreateTime(java.time.LocalDateTime.now().toString());
        return jobMapper.insert(work);
    }

    @Override
    public boolean deleteWork(int id) {
        jobMapper.deleteMerchantRequirementByJobId(id);
        jobMapper.deleteWorkAssignmentsByJobId(id);
        return jobMapper.deleteById(id) > 0;

    }

    @Override
    public boolean updateWork(Job work) {
        boolean updated = jobMapper.update(work) > 0;
        if (updated && work.getTime() != null) {
            // 保存原有每个时段已招人数
            Map<String, Integer> oldFilledCounts = new HashMap<>();
            List<Map<String, Object>> oldRequirements = assignmentMapper.getRequirementsByJobId(work.getId());
            if (oldRequirements != null) {
                for (Map<String, Object> req : oldRequirements) {
                    String key = req.get("day_of_week").toString() + "_" + req.get("time_slot").toString();
                    int filled = Integer.parseInt(req.get("filled_count").toString());
                    oldFilledCounts.put(key, filled);
                }
            }

            // 删除旧需求
            jobMapper.deleteMerchantRequirementByJobId(work.getId());

            // 插入新需求，保留旧已招人数
            handleMerchantRequirementsWithOldCounts(
                    work.getId(),
                    work.getUserId(),
                    work.getTime(),
                    work.getRemunerationType(),
                    work.getRecruitmentLimit(),
                    oldFilledCounts
            );

            // 自动恢复兼职状态：如果原本“已招满”但扩招后名额增加
            if ("已招满".equals(work.getStatus())) {
                // 使用 Mapper 查询已接受人数
                int acceptedCount = jobApplicationMapper.getAcceptedApplicationsCount(work.getId());
                int limit = parseRecruitmentLimit(work.getRecruitmentLimit());
                if (limit > acceptedCount) {
                    jobMapper.updateJobStatus(work.getId(), "进行中");
                }
            }
        }
        return updated;
    }

    private void handleMerchantRequirementsWithOldCounts(
            int jobId, int merchantId, String timeJson,
            String remunerationType, String recruitmentLimit,
            Map<String, Integer> oldFilledCounts) {

        if (timeJson == null || timeJson.isEmpty()) return;

        try {
            JSONObject timeObj = JSONObject.parseObject(timeJson);
            int neededCount = 1;   // 每个时段只招1人

            if (timeObj.containsKey("timeSlots")) {
                JSONArray timeSlots = timeObj.getJSONArray("timeSlots");
                for (int i = 0; i < timeSlots.size(); i++) {
                    JSONObject slot = timeSlots.getJSONObject(i);
                    int dayOfWeek = slot.getIntValue("day");
                    String timeSlot = slot.getString("period");

                    String key = dayOfWeek + "_" + timeSlot;
                    int oldFilled = oldFilledCounts.getOrDefault(key, 0);
                    int finalFilled = Math.min(oldFilled, neededCount);

                    jobMapper.insertMerchantRequirementWithFilled(
                            jobId, merchantId, dayOfWeek, timeSlot, neededCount, finalFilled);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int parseRecruitmentLimit(String limit) {
        if (limit == null || limit.isEmpty()) return 0;
        try {
            return Integer.parseInt(limit.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Job getWorkById(int id) {
        return jobMapper.selectById(id);
    }

    @Override
    public List<Job> getAllWorks() {
        return jobMapper.selectAll();
    }

    @Override
    public List<Job> getWorksByUserId(int userId) {
        return jobMapper.selectByUserId(userId);
    }

    @Override
    public List<Job> getAvailableWorks() {
        return jobMapper.selectAvailable();
    }

    @Override
    public WorkDTO addWorkDTO(WorkDTO workDTO) {
        Job work = toEntity(workDTO);
        int result = addWork(work);
        if (result > 0) {
            handleMerchantRequirements(work.getId(), workDTO.getUserId(), workDTO.getTime(),
                    workDTO.getRemunerationType(), workDTO.getRecruitmentLimit());
            return toDTO(work);
        }
        return null;
    }

    private void handleMerchantRequirements(int jobId, int merchantId, String timeJson,
                                            String remunerationType, String recruitmentLimit) {
        handleMerchantRequirementsWithOldCounts(jobId, merchantId, timeJson,
                remunerationType, recruitmentLimit, new HashMap<>());
    }

    @Override
    public boolean acceptWork(int workId, int userId) {
        Job work = jobMapper.selectById(workId);
        if (work == null || !"进行中".equals(work.getStatus())) {
            return false;
        }
        work.setStatus("已接取");
        return jobMapper.update(work) > 0;
    }

    private WorkDTO toDTO(Job work) {
        if (work == null) return null;
        WorkDTO dto = new WorkDTO();
        dto.setId(work.getId());
        dto.setTitle(work.getTitle());
        dto.setContent(work.getContent());
        dto.setAddress(work.getAddress());
        dto.setPhone(work.getPhone());
        dto.setImageUrl(work.getImageUrl());
        dto.setUserId(work.getUserId());
        dto.setStatus(work.getStatus());
        dto.setCreateTime(work.getCreateTime());
        dto.setSalary(work.getSalary());
        dto.setTime(work.getTime());
        dto.setRemunerationType(work.getRemunerationType());
        dto.setCategory(work.getCategory());
        dto.setRecruitmentLimit(work.getRecruitmentLimit());
        dto.setApplicationCount(work.getApplicationCount());
        dto.setTags(work.getTags());

        return dto;
    }

    private Job toEntity(WorkDTO dto) {
        if (dto == null) return null;
        Job work = new Job();
        work.setId(dto.getId() != null ? dto.getId() : 0);
        work.setTitle(dto.getTitle());
        work.setContent(dto.getContent());
        work.setAddress(dto.getAddress());
        work.setPhone(dto.getPhone());
        work.setImageUrl(dto.getImageUrl());
        work.setUserId(dto.getUserId() != null ? dto.getUserId() : 0);
        work.setStatus(dto.getStatus() != null ? dto.getStatus() : "进行中");
        work.setCreateTime(dto.getCreateTime());
        work.setSalary(dto.getSalary());
        work.setTime(dto.getTime());
        work.setRemunerationType(dto.getRemunerationType());
        work.setCategory(dto.getCategory() != null ? dto.getCategory() : "all");
        work.setRecruitmentLimit(dto.getRecruitmentLimit());
        work.setApplicationCount(dto.getApplicationCount() != null ? dto.getApplicationCount() : "0");
        work.setTags(dto.getTags());

        return work;
    }
    @Override
    public List<Map<String, Object>> getWorkingStudentsByMerchant(int merchantId) {
        // 1. 获取该商户所有进行中的兼职
        List<Job> jobs = jobMapper.selectByUserId(merchantId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Job job : jobs) {
            // 只显示未结束的兼职
            if ("已结束".equals(job.getStatus())) continue;

            // 2. 获取该兼职下所有状态为 assigned/checked_in 的工作分配
            List<Map<String, Object>> assignments = workAssignmentMapper.getActiveAssignmentsByJobId(job.getId());
            if (assignments.isEmpty()) continue;

            // 3. 按学生分组
            Map<Integer, List<Map<String, Object>>> studentGroups = new LinkedHashMap<>();
            for (Map<String, Object> wa : assignments) {
                Integer studentId = toInteger(wa.get("student_id"));
                studentGroups.computeIfAbsent(studentId, k -> new ArrayList<>()).add(wa);
            }

            // 4. 构建该兼职的学生列表
            List<Map<String, Object>> studentList = new ArrayList<>();
            for (Map.Entry<Integer, List<Map<String, Object>>> entry : studentGroups.entrySet()) {
                Integer studentId = entry.getKey();
                List<Map<String, Object>> workList = entry.getValue();
                User student = userService.getUserById(studentId);
                if (student == null) continue;

                Map<String, Object> studentInfo = new HashMap<>();
                studentInfo.put("studentId", student.getId());
                studentInfo.put("studentName", student.getName());
                studentInfo.put("phone", student.getPhone());
                studentInfo.put("credit", student.getCredit());
                studentInfo.put("assignedSlots", workList.size());
                studentInfo.put("jobId", job.getId());

                // 工作状态：取第一个非 completed/cancelled 的状态
                String displayStatus = "已分配";
                for (Map<String, Object> w : workList) {
                    String st = (String) w.get("status");
                    if ("checked_in".equals(st)) { displayStatus = "已签到"; break; }
                }
                studentInfo.put("status", displayStatus);

                studentList.add(studentInfo);
            }

            // 5. 计算已招人数
            int hiredCount = jobApplicationMapper.getAcceptedApplicationsCount(job.getId());

            Map<String, Object> jobGroup = new HashMap<>();
            jobGroup.put("jobId", job.getId());
            jobGroup.put("jobTitle", job.getTitle());
            jobGroup.put("recruitmentLimit", job.getRecruitmentLimit());
            jobGroup.put("hiredCount", hiredCount);
            jobGroup.put("students", studentList);

            result.add(jobGroup);
        }
        return result;
    }
    @Override
    public List<Map<String, Object>> getHistoryStudentsByMerchant(int merchantId) {
        List<Map<String, Object>> assignments = workAssignmentMapper.getHistoryAssignmentsByMerchant(merchantId);
        if (assignments == null || assignments.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> wa : assignments) {
            // 安全转换（MyBatis 可能返回 Long）
            Integer workAssignmentId = TypeConversionUtils.toInteger(wa.get("lastWorkAssignmentId"));
            Integer studentId = TypeConversionUtils.toInteger(wa.get("student_id"));
            if (workAssignmentId == null || studentId == null) continue;

            // 检查商户是否已评价
            Map<String, Object> eval = evaluationMapper.getMerchantEvaluationByAssignmentId(workAssignmentId);
            boolean evaluated = (eval != null);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", studentId);
            item.put("studentName", wa.get("studentName"));
            item.put("credit", wa.get("credit"));
            item.put("lastJobTitle", wa.get("lastJobTitle"));
            item.put("lastJobId", wa.get("lastJobId"));
            item.put("lastWorkDate", wa.get("lastWorkDate"));
            item.put("lastWorkAssignmentId", workAssignmentId);
            item.put("evaluated", evaluated);
            result.add(item);
        }
        return result;
    }

    private WorkDTO toDTOWithPublisher(Job work) {
        if (work == null) return null;
        WorkDTO dto = toDTO(work);
        User publisher = userService.getUserById(work.getUserId());
        dto.setUserName(publisher != null ? publisher.getName() : "未知用户");
        return dto;
    }

    @Override
    public List<WorkDTO> getAllWorkDTOs() {
        List<Job> jobs = jobMapper.selectAll();
        List<WorkDTO> result = new ArrayList<>();
        for (Job job : jobs) {
            result.add(toDTOWithPublisher(job));
        }
        return result;
    }

    @Override
    public List<WorkDTO> getAvailableWorkDTOs() {
        List<Job> jobs = jobMapper.selectAvailable();
        List<WorkDTO> result = new ArrayList<>();
        for (Job job : jobs) {
            result.add(toDTOWithPublisher(job));
        }
        return result;
    }

    @Override
    public List<WorkDTO> getMyWorkDTOs(int userId) {
        List<Job> jobs = jobMapper.selectByUserId(userId);
        List<WorkDTO> result = new ArrayList<>();
        for (Job job : jobs) {
            result.add(toDTOWithPublisher(job));
        }
        return result;
    }

    @Override
    public WorkDTO getWorkDTOById(int id) {
        Job job = jobMapper.selectById(id);
        if (job == null) return null;
        WorkDTO dto = toDTOWithPublisher(job);
        dto.setRequirements(assignmentMapper.getRequirementsByJobId(id));
        return dto;
    }

    @Override
    public WorkDTO addWorkWithPublisher(WorkDTO workDTO, int userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        workDTO.setUserId(userId);
        workDTO.setApplicationCount("0");
        return addWorkDTO(workDTO);
    }

    @Override
    public void updateWorkWithPermission(Job job, int userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Job original = jobMapper.selectById(job.getId());
        if (original == null) {
            throw new BusinessException("兼职信息不存在");
        }
        if (!"管理员".equals(user.getIdentity()) && original.getUserId() != userId) {
            throw new BusinessException(403, "只能修改自己发布的兼职");
        }
        if (job.getStatus() == null) {
            job.setStatus("进行中");
        }
        if (job.getCreateTime() == null) {
            job.setCreateTime(original.getCreateTime());
        }
        if (!updateWork(job)) {
            throw new BusinessException("修改失败");
        }
    }

    @Override
    public void deleteWorkWithPermission(int jobId, int userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException("兼职信息不存在");
        }
        if (!"管理员".equals(user.getIdentity()) && job.getUserId() != userId) {
            throw new BusinessException(403, "只能删除自己发布的兼职");
        }
        if (!deleteWork(jobId)) {
            throw new BusinessException("删除失败");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> applyForJob(int jobId, int userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!creditService.checkCreditPermission(userId)) {
            throw new BusinessException("您的信用分低于50，无法申请兼职。请通过完成工作或申诉恢复信用分。");
        }

        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException("兼职信息不存在");
        }
        if ("已招满".equals(job.getStatus())) {
            throw new BusinessException("该兼职已招满");
        }

        boolean hasAccepted = jobApplicationService.hasAcceptedApplication(jobId, userId);
        if (hasAccepted) {
            throw new BusinessException("您已有一个被接受的申请，不能重复申请");
        }

        int acceptedCount = jobApplicationService.getAcceptedApplicationsCount(jobId);
        int recruitmentLimit = parseRecruitmentLimit(job.getRecruitmentLimit());
        if (recruitmentLimit > 0 && acceptedCount >= recruitmentLimit) {
            job.setStatus("已招满");
            jobMapper.update(job);
            throw new BusinessException("该兼职已招满");
        }

        int applicationCount = Integer.parseInt(job.getApplicationCount() != null ? job.getApplicationCount() : "0") + 1;
        job.setApplicationCount(String.valueOf(applicationCount));

        if (jobMapper.update(job) <= 0) {
            throw new BusinessException("申请失败");
        }

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setStudentId(userId);
        application.setMerchantId(job.getUserId());
        application.setStudentName(user.getName());
        application.setStatus("pending");

        User student = userService.getUserById(userId);
        if (student != null && student.getTimepreference() != null) {
            application.setTimeAvailability(student.getTimepreference());
        }

        String createResult = jobApplicationService.createApplication(application);
        if (!"success".equals(createResult)) {
            throw new BusinessException("申请失败");
        }

        List<JobApplicationDTO> applications = jobApplicationService.getApplicationsByJobId(jobId);
        int applicationId = 0;
        if (applications != null && !applications.isEmpty()) {
            applicationId = applications.get(0).getId();
        }

        Message message = new Message();
        message.setSenderId(userId);
        message.setReceiverId(job.getUserId());
        message.setType("apply_job");
        message.setContent(user.getName() + " 申请了您的兼职: " + job.getTitle());
        message.setRelatedId(applicationId);
        messageService.sendMessage(message);

        Map<String, Object> result = new HashMap<>();
        result.put("applicationId", applicationId);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> cancelAssignment(int workAssignmentId, int userId) {
        Map<String, Object> assignment = workAssignmentMapper.getWorkAssignmentById(workAssignmentId);
        if (assignment == null) {
            throw new BusinessException("工作分配不存在");
        }

        User user = userService.getUserById(userId);
        if (user == null || (!"管理员".equals(user.getIdentity()) && !"商户".equals(user.getIdentity()))) {
            throw new BusinessException(403, "无权限");
        }

        String status = (String) assignment.get("status");
        if (!"assigned".equals(status) && !"checked_in".equals(status)) {
            throw new BusinessException("当前状态不能取消");
        }

        if ("checked_in".equals(status)) {
            attendanceMapper.updateAttendanceStatus(workAssignmentId, "leave");
        }

        workAssignmentMapper.updateWorkAssignmentStatus(workAssignmentId, "cancelled");

        Integer jobId = toInteger(assignment.get("job_id"));
        String timeSlot = (String) assignment.get("time_slot");
        java.sql.Date workDateSql = (java.sql.Date) assignment.get("work_date");
        if (jobId == null || timeSlot == null || workDateSql == null) {
            throw new BusinessException(500, "数据不完整");
        }

        LocalDate workDate = workDateSql.toLocalDate();
        int dayOfWeek = workDate.getDayOfWeek().getValue();
        jobMapper.updateMerchantRequirementFilledCount(jobId, dayOfWeek, timeSlot, -1);

        Integer studentId = toInteger(assignment.get("student_id"));
        if (studentId != null) {
            userService.updateWorkingStatus(studentId, 0);
            // 恢复 student_schedule 中该时段的可用性
            assignmentMapper.updateStudentSchedule(studentId, dayOfWeek, timeSlot, 1);
        }


        if (studentId != null) {
            User student = userService.getUserById(studentId);
            if (student != null) {
                String jobTitle = (String) assignment.get("jobTitle");
                if (jobTitle == null && jobId != null) {
                    Job job = jobMapper.selectById(jobId);
                    if (job != null) jobTitle = job.getTitle();
                }
                if (jobTitle == null) jobTitle = "未知兼职";

                Message msg = new Message();
                msg.setSenderId(userId);
                msg.setReceiverId(studentId);
                msg.setType("work_cancelled");
                msg.setContent(String.format("您在 %s %s 的工作【%s】已被商户取消，名额已释放。",
                        workDate, getTimeSlotChinese(timeSlot), jobTitle));
                msg.setRelatedId(jobId != null ? jobId : 0);
                messageService.sendMessage(msg);
            }
        }
        if (studentId != null) cacheClearService.clearStudentCache(studentId);
        if (jobId != null) cacheClearService.clearJobCache(jobId);

        return Map.of("released", true);
    }

    @Override
    @Transactional
    public Map<String, Object> fireStudent(int studentId, int jobId, int operatorId) {
        User operator = userService.getUserById(operatorId);
        Job job = jobMapper.selectById(jobId);
        if (operator == null || job == null ||
                (!"管理员".equals(operator.getIdentity()) && job.getUserId() != operatorId)) {
            throw new BusinessException(403, "无权限");
        }

        // 获取该学生在该兼职下所有活跃的工作分配（assigned/checked_in）
        List<Map<String, Object>> assignments = workAssignmentMapper.getActiveAssignmentsByJobIdAndStudent(jobId, studentId);
        if (assignments == null || assignments.isEmpty()) {
            throw new BusinessException("该学生在该兼职下没有活跃的工作分配");
        }

        int released = 0;
        // 记录需要恢复的时段（避免重复恢复）
        Set<String> recoveredSlots = new HashSet<>();
        for (Map<String, Object> wa : assignments) {
            Integer waId = toInteger(wa.get("id"));
            // 更新工作分配状态为 cancelled
            workAssignmentMapper.updateWorkAssignmentStatus(waId, "cancelled");

            java.sql.Date workDate = (java.sql.Date) wa.get("work_date");
            String timeSlot = (String) wa.get("time_slot");
            if (workDate == null || timeSlot == null) continue;
            LocalDate wd = workDate.toLocalDate();
            int dayOfWeek = wd.getDayOfWeek().getValue();
            // 释放名额（每个时段减少1）
            jobMapper.updateMerchantRequirementFilledCount(jobId, dayOfWeek, timeSlot, -1);
            released++;

            // 恢复学生 schedule（如果该时段没有其他工作安排）
            String slotKey = dayOfWeek + "_" + timeSlot;
            if (!recoveredSlots.contains(slotKey)) {
                // 检查该学生该时段是否还有其他活跃工作分配
                List<Map<String, Object>> otherAssignments = workAssignmentMapper.getActiveAssignmentsByDayAndSlot(studentId, dayOfWeek, timeSlot);
                if (otherAssignments == null || otherAssignments.isEmpty()) {
                    assignmentMapper.updateStudentSchedule(studentId, dayOfWeek, timeSlot, 1);
                    recoveredSlots.add(slotKey);
                }
            }
        }

        // 更新学生工作状态为空闲
        userService.updateWorkingStatus(studentId, 0);

        // 将该学生的申请状态改为 rejected（如果存在且为 accepted）
        JobApplication application = jobApplicationMapper.getApplicationByJobAndStudent(jobId, studentId);
        if (application != null && "accepted".equals(application.getStatus())) {
            jobApplicationMapper.updateApplicationStatus(application.getId(), "rejected", "被商户解雇");
        }

        // 发送消息通知学生
        User student = userService.getUserById(studentId);
        Message msg = new Message();
        msg.setSenderId(operatorId);
        msg.setReceiverId(studentId);
        msg.setType("student_fired");
        msg.setContent(String.format("很遗憾，您已被商户%s从兼职【%s】中解雇，所有工作安排已取消。",
                operator.getName(), job.getTitle()));
        msg.setRelatedId(jobId);
        messageService.sendMessage(msg);

        // 清除缓存
        cacheClearService.clearStudentCache(studentId);
        cacheClearService.clearJobCache(jobId);
        // 同步兼职状态（可能从已招满变为进行中）
        syncJobStatusByRequirements(jobId);

        return Map.of("released", released);
    }

    @Override
    public List<Map<String, Object>> getStudentAcceptedJobs(int studentId) {
        List<Map<String, Object>> assignments = workAssignmentMapper.getStudentAcceptedJobs(studentId);
        if (assignments == null || assignments.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Integer, List<Map<String, Object>>> jobGroups = new LinkedHashMap<>();
        for (Map<String, Object> wa : assignments) {
            Integer jobId = toInteger(wa.get("job_id"));
            jobGroups.computeIfAbsent(jobId, k -> new ArrayList<>()).add(wa);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Map<String, Object>>> entry : jobGroups.entrySet()) {
            Integer jobId = entry.getKey();
            List<Map<String, Object>> workList = entry.getValue();

            Map<String, Object> first = workList.get(0);
            Job job = jobMapper.selectById(jobId);

            Map<String, Object> jobGroup = new LinkedHashMap<>();
            jobGroup.put("jobId", jobId);
            jobGroup.put("jobTitle", first.get("jobTitle"));
            jobGroup.put("salary", first.get("salary"));
            jobGroup.put("address", first.get("address"));
            jobGroup.put("remunerationType", first.get("remunerationType"));
            jobGroup.put("merchantId", first.get("merchantId"));
            jobGroup.put("merchantName", first.get("merchantName"));
            jobGroup.put("merchantPhone", first.get("merchantPhone"));
            jobGroup.put("recruitmentLimit", first.get("recruitmentLimit"));
            jobGroup.put("assignedSlots", workList.size());
            jobGroup.put("jobStatus", job != null ? job.getStatus() : "未知");

            String displayStatus = "已分配";
            for (Map<String, Object> w : workList) {
                String st = (String) w.get("status");
                if ("checked_in".equals(st)) { displayStatus = "已签到"; break; }
            }
            jobGroup.put("workStatus", displayStatus);

            List<Map<String, Object>> slotList = new ArrayList<>();
            for (Map<String, Object> w : workList) {
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("workDate", w.get("work_date"));
                slot.put("timeSlot", w.get("time_slot"));
                slot.put("status", w.get("status"));
                slotList.add(slot);
            }
            jobGroup.put("timeSlots", slotList);

            result.add(jobGroup);
        }
        return result;
    }

    @Override
    public Map<String, Object> studentResign(int studentId, int jobId) {
        User student = userService.getUserById(studentId);
        if (student == null) {
            throw new BusinessException("学生不存在");
        }
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException("兼职不存在");
        }

        List<Map<String, Object>> assignments = workAssignmentMapper.getActiveAssignmentsByJobIdAndStudent(jobId, studentId);
        if (assignments == null || assignments.isEmpty()) {
            throw new BusinessException("该兼职下没有进行中的工作安排");
        }

        User merchant = userService.getUserById(job.getUserId());
        Message msg = new Message();
        msg.setSenderId(studentId);
        msg.setReceiverId(job.getUserId());
        msg.setType("student_resign");
        msg.setContent(String.format("学生 %s 申请辞去兼职【%s】的工作（共%d个已排时段），请您确认是否同意。",
                student.getName(), job.getTitle(), assignments.size()));
        msg.setRelatedId(jobId);
        messageService.sendMessage(msg);

        // 注意：此处只是发送申请，尚未真正释放名额，所以不立即清除缓存
        return Map.of("success", true, "message", "辞职申请已发送，等待商户确认");
    }

    @Override
    @Transactional
    public Map<String, Object> approveStudentResign(int studentId, int jobId, int merchantId) {
        User student = userService.getUserById(studentId);
        if (student == null) {
            throw new BusinessException("学生不存在");
        }
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException("兼职不存在");
        }
        if (job.getUserId() != merchantId) {
            throw new BusinessException(403, "无权限操作");
        }

        List<Map<String, Object>> assignments = workAssignmentMapper.getActiveAssignmentsByJobIdAndStudent(jobId, studentId);
        if (assignments == null || assignments.isEmpty()) {
            throw new BusinessException("该兼职下没有进行中的工作安排");
        }

        int released = 0;
        for (Map<String, Object> wa : assignments) {
            Integer waId = toInteger(wa.get("id"));
            workAssignmentMapper.updateWorkAssignmentStatus(waId, "cancelled");

            java.sql.Date workDate = (java.sql.Date) wa.get("work_date");
            String timeSlot = (String) wa.get("time_slot");
            if (workDate != null && timeSlot != null) {
                LocalDate wd = workDate.toLocalDate();
                int dayOfWeek = wd.getDayOfWeek().getValue();
                jobMapper.updateMerchantRequirementFilledCount(jobId, dayOfWeek, timeSlot, -1);
                // 恢复 student_schedule
                assignmentMapper.updateStudentSchedule(studentId, dayOfWeek, timeSlot, 1);
            }
            released++;
        }

        userService.updateWorkingStatus(studentId, 0);

        // 更新原始辞职请求消息内容
        Message originalMsg = messageService.findResignRequestMessage(studentId, merchantId, "student_resign", jobId);
        if (originalMsg != null) {
            messageService.updateMessageContent(originalMsg.getId(),
                    String.format("学生 %s 申请辞去兼职【%s】的工作（共%d个已排时段）。\n【已同意】— 所有工作安排已取消（共释放%d个时段）。",
                            student.getName(), job.getTitle(), assignments.size(), released));
        }

        Message msg = new Message();
        msg.setSenderId(merchantId);
        msg.setReceiverId(studentId);
        msg.setType("student_resign");
        msg.setContent(String.format("商户已同意您的辞职申请，兼职【%s】的所有工作安排已取消（共释放%d个时段）。",
                job.getTitle(), released));
        msg.setRelatedId(jobId);
        messageService.sendMessage(msg);


        cacheClearService.clearStudentCache(studentId);
        cacheClearService.clearJobCache(jobId);
        syncJobStatusByRequirements(jobId);

        return Map.of("success", true, "message", "已同意辞职，时段已释放", "released", released);
    }

    @Override
    public boolean checkTimeSlotOccupied(int jobId, String date, String timeSlot) {
        try {
            LocalDate workDate = LocalDate.parse(date);
            int dayOfWeek = workDate.getDayOfWeek().getValue();

            List<Map<String, Object>> requirements = assignmentMapper.getRequirementsByJobId(jobId);
            if (requirements != null) {
                for (Map<String, Object> req : requirements) {
                    int reqDay = Integer.parseInt(req.get("day_of_week").toString());
                    String reqSlot = (String) req.get("time_slot");

                    if (reqDay == dayOfWeek && reqSlot.equals(timeSlot)) {
                        int needed = Integer.parseInt(req.get("needed_count").toString());
                        int filled = Integer.parseInt(req.get("filled_count").toString());
                        return filled >= needed;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

    private String getTimeSlotChinese(String slot) {
        if ("morning".equals(slot)) return "上午";
        if ("afternoon".equals(slot)) return "下午";
        if ("evening".equals(slot)) return "晚上";
        return slot;
    }
    // 在 JobServiceImpl 中添加方法
    private void syncJobStatusByRequirements(int jobId) {
        List<Map<String, Object>> reqs = assignmentMapper.getRequirementsByJobId(jobId);
        if (reqs == null || reqs.isEmpty()) return;
        boolean anyOpen = reqs.stream().anyMatch(r -> {
            int needed = Integer.parseInt(r.get("needed_count").toString());
            int filled = Integer.parseInt(r.get("filled_count").toString());
            return filled < needed;
        });
        String newStatus = anyOpen ? "进行中" : "已招满";
        Job job = jobMapper.selectById(jobId);
        if (job != null && !newStatus.equals(job.getStatus())) {
            jobMapper.updateJobStatus(jobId, newStatus);
        }
    }
}
