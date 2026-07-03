package com.xk.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xk.common.Result;
import com.xk.dto.JobApplicationDTO;
import com.xk.entity.JobApplication;
import com.xk.entity.Message;
import com.xk.entity.User;
import com.xk.exception.BusinessException;
import com.xk.service.JobApplicationService;
import com.xk.service.MessageService;
import com.xk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/application")
public class JobApplicationController {

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] DAY_NAMES = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    @PostMapping("/create")
    public Result<Map<String, Object>> createApplication(@RequestBody JobApplication application) {
        String res = jobApplicationService.createApplication(application);
        if (!"success".equals(res)) {
            throw new BusinessException(res);
        }
        User student = userService.getUserById(application.getStudentId());
        Message message = new Message();
        message.setSenderId(application.getStudentId());
        message.setReceiverId(application.getMerchantId());
        message.setType("apply_job");
        message.setContent(buildApplyMessage(student.getName(), application.getSelectedDates()));
        message.setRelatedId(application.getId());
        messageService.sendMessage(message);
        return Result.success("申请成功", Map.of("applicationId", application.getId()));
    }

    private String buildApplyMessage(String studentName, String selectedDatesJson) {
        StringBuilder sb = new StringBuilder();
        sb.append(studentName).append(" 申请了您的兼职\n\n");
        if (selectedDatesJson != null && !selectedDatesJson.isEmpty()) {
            try {
                List<Map<String, Object>> dates = objectMapper.readValue(selectedDatesJson,
                        new TypeReference<List<Map<String, Object>>>() {});
                if (!dates.isEmpty()) {
                    sb.append("申请时段：\n");
                    for (Map<String, Object> d : dates) {
                        String dateStr = (String) d.get("date");
                        String slot = (String) d.get("timeSlot");
                        String slotName = "morning".equals(slot) ? "上午" :
                                          "afternoon".equals(slot) ? "下午" : "晚上";
                        try {
                            java.time.LocalDate ld = java.time.LocalDate.parse(dateStr);
                            int dow = ld.getDayOfWeek().getValue();
                            sb.append("  ").append(ld).append(" ").append(DAY_NAMES[dow]).append(" ").append(slotName).append("\n");
                        } catch (Exception e) {
                            sb.append("  ").append(dateStr).append(" ").append(slotName).append("\n");
                        }
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                System.err.println("解析申请时段失败: " + e.getMessage());
            }
        }
        sb.append("请及时处理");
        return sb.toString();
    }

    @GetMapping("/job/{jobId}")
    public Result<List<JobApplicationDTO>> getJobApplications(@PathVariable int jobId) {
        return Result.success(jobApplicationService.getApplicationsByJobId(jobId));
    }

    @GetMapping("/student/{studentId}")
    public Result<List<JobApplicationDTO>> getStudentApplications(@PathVariable int studentId) {
        return Result.success(jobApplicationService.getApplicationsByStudentId(studentId));
    }

    @GetMapping("/merchant/{merchantId}")
    public Result<List<JobApplicationDTO>> getMerchantApplications(@PathVariable int merchantId) {
        return Result.success(jobApplicationService.getApplicationsByMerchantId(merchantId));
    }

    @PutMapping("/status")
    public Result<?> updateStatus(@RequestBody Map<String, Object> request) {
        int id = (Integer) request.get("id");
        String status = (String) request.get("status");
        int operatorId = (Integer) request.get("operatorId");
        String phone = (String) request.get("phone");
        String address = (String) request.get("address");
        String messageContent = (String) request.get("message");
        boolean forceAccept = false;
        Object forceAcceptObj = request.get("forceAccept");
        if (forceAcceptObj != null) {
            if (forceAcceptObj instanceof Boolean) {
                forceAccept = (Boolean) forceAcceptObj;
            } else if (forceAcceptObj instanceof String) {
                forceAccept = Boolean.parseBoolean((String) forceAcceptObj);
            } else if (forceAcceptObj instanceof Number) {
                forceAccept = ((Number) forceAcceptObj).intValue() != 0;
            }
        }
        String res = jobApplicationService.updateApplicationStatus(id, status, messageContent, forceAccept);
        if (!"success".equals(res)) {
            throw new BusinessException(res);
        }
        JobApplicationDTO application = jobApplicationService.getApplicationById(id);
        if (application != null) {
            User operator = userService.getUserById(operatorId);
            Message message = new Message();
            message.setSenderId(operatorId);
            message.setReceiverId(application.getStudentId());
            message.setType("application_status");
            if ("accepted".equals(status)) {
                StringBuilder content = new StringBuilder("恭喜！您的兼职申请已被商户接受\n\n");
                if (phone != null && !phone.isEmpty()) content.append("联系电话：").append(phone).append("\n");
                if (address != null && !address.isEmpty()) content.append("工作地址：").append(address).append("\n");
                if (messageContent != null && !messageContent.isEmpty()) content.append("商户留言：").append(messageContent);
                message.setContent(content.toString());
            } else if ("rejected".equals(status)) {
                StringBuilder content = new StringBuilder("很遗憾，您的兼职申请已被商户拒绝\n\n");
                if (messageContent != null && !messageContent.isEmpty()) content.append("拒绝原因：").append(messageContent);
                message.setContent(content.toString());
            }
            message.setRelatedId(application.getJobId());
            messageService.sendMessage(message);
        }
        return Result.successMsg("状态更新成功");
    }

    @GetMapping("/{id}")
    public Result<JobApplicationDTO> getApplicationById(@PathVariable int id) {
        JobApplicationDTO application = jobApplicationService.getApplicationById(id);
        return application != null ? Result.success(application) : Result.error("申请不存在");
    }
}
