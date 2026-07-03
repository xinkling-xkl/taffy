package com.xk.controller;

import com.xk.common.Result;
import com.xk.dto.WorkDTO;
import com.xk.entity.Job;
import com.xk.entity.Message;
import com.xk.entity.User;
import com.xk.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class JobController {

    @Autowired
    private JobService jobService;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private CreditService creditService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @GetMapping("/job/list")
    public Result<List<WorkDTO>> getAllWorks() {
        return Result.success(jobService.getAllWorkDTOs());
    }

    @GetMapping("/job/available")
    public Result<List<WorkDTO>> getAvailableWorks() {
        return Result.success(jobService.getAvailableWorkDTOs());
    }

    @GetMapping("/job/my")
    public Result<List<WorkDTO>> getMyWorks(@RequestParam("userId") int userId) {
        return Result.success(jobService.getMyWorkDTOs(userId));
    }

    @GetMapping("/job/{id}")
    public Result<WorkDTO> getWorkById(@PathVariable("id") int id) {
        WorkDTO dto = jobService.getWorkDTOById(id);
        if (dto == null) {
            return Result.error("兼职信息不存在");
        }
        return Result.success(dto);
    }

    @PostMapping("/job/add")
    public Result<WorkDTO> addWork(@RequestBody WorkDTO workDTO, @RequestParam("userId") int userId) {
        WorkDTO created = jobService.addWorkWithPublisher(workDTO, userId);
        return Result.success("发布成功", created);
    }

    @PutMapping("/job/update")
    public Result<?> updateWork(@RequestBody Job job, @RequestParam("userId") int userId) {
        jobService.updateWorkWithPermission(job, userId);
        return Result.successMsg("修改成功");
    }

    @DeleteMapping("/job/{id}")
    public Result<?> deleteWork(@PathVariable("id") int id, @RequestParam("userId") int userId) {
        jobService.deleteWorkWithPermission(id, userId);
        return Result.successMsg("删除成功");
    }

    @PostMapping("/job/accept")
    public Result<?> acceptWork(@RequestParam("workId") int workId, @RequestParam("userId") int userId) {
        boolean success = jobService.acceptWork(workId, userId);
        return success ? Result.successMsg("接受兼职成功") : Result.error("接受兼职失败");
    }

    @PostMapping("/job/complete")
    public Result<?> completeWork(@RequestParam("workId") int workId, @RequestParam("userId") int userId) {
        creditService.updateCreditScore(userId, 10, "完成兼职", "complete_job");
        return Result.successMsg("完成兼职成功");
    }

    @PostMapping("/job/cancel")
    public Result<?> cancelWork(@RequestParam("workId") int workId, @RequestParam("userId") int userId) {
        return Result.successMsg("取消兼职成功");
    }

    @PostMapping("/job/apply/{jobId}")
    public Result<Map<String, Object>> applyWork(@PathVariable("jobId") int jobId, @RequestParam("userId") int userId) {
        Map<String, Object> data = jobService.applyForJob(jobId, userId);
        return Result.success("申请成功", data);
    }

    @PostMapping("/job/cancel-assignment")
    public Result<?> cancelAssignment(@RequestParam int workAssignmentId, @RequestParam int userId) {
        jobService.cancelAssignment(workAssignmentId, userId);
        return Result.successMsg("取消成功，名额已释放");
    }

    @PostMapping("/job/invite/{jobId}")
    public Result<?> inviteStudent(@PathVariable("jobId") int jobId,
                                   @RequestParam("studentId") int studentId,
                                   @RequestParam("merchantId") int merchantId,
                                   @RequestParam(value = "reason", required = false) String reason) {
        Job job = jobService.getWorkById(jobId);
        if (job == null) return Result.error("兼职信息不存在");

        User student = userService.getUserById(studentId);
        if (student == null) return Result.error("学生不存在");

        User merchant = userService.getUserById(merchantId);
        if (merchant == null) return Result.error("商户不存在");

        String inviteContent = String.format(
            "您被邀请参与兼职：%s\n薪资：%s\n地点：%s\n工作时间：%s\n商户：%s\n联系电话：%s\n%s请及时查看并回复是否接受邀请",
            job.getTitle(), job.getSalary(), job.getAddress(), job.getTime(),
            merchant.getName(), merchant.getPhone(),
            reason != null && !reason.isEmpty() ? "邀请理由：" + reason + "\n" : ""
        );

        Message message = new Message();
        message.setSenderId(merchantId);
        message.setReceiverId(studentId);
        message.setType("invite_student");
        message.setContent(inviteContent);
        message.setRelatedId(jobId);

        String res = messageService.sendMessage(message);
        return "success".equals(res) ? Result.successMsg("邀请成功") : Result.error("邀请失败");
    }

    @PostMapping("/job/late")
    public Result<?> reportLate(@RequestParam("workId") int workId, @RequestParam("userId") int userId) {
        creditService.updateCreditScore(userId, -5, "迟到", "late");
        return Result.successMsg("迟到记录成功");
    }

    @PostMapping("/job/absent")
    public Result<?> reportAbsent(@RequestParam("workId") int workId, @RequestParam("userId") int userId) {
        creditService.updateCreditScore(userId, -10, "缺席", "absent");
        return Result.successMsg("缺席记录成功");
    }

    @GetMapping("/job/check-accepted-application")
    public Result<Map<String, Object>> checkAcceptedApplication(@RequestParam("studentId") int studentId) {
        List<com.xk.dto.JobApplicationDTO> applications = jobApplicationService.getApplicationsByStudentId(studentId);
        boolean hasAccepted = false;
        if (applications != null) {
            for (com.xk.dto.JobApplicationDTO app : applications) {
                if ("accepted".equals(app.getStatus())) {
                    hasAccepted = true;
                    break;
                }
            }
        }
        return Result.success(Map.of("hasAccepted", hasAccepted));
    }

    @GetMapping("/job/working-students")
    public Result<List<Map<String, Object>>> getWorkingStudents(@RequestParam int merchantId) {
        return Result.success(jobService.getWorkingStudentsByMerchant(merchantId));
    }

    @PostMapping("/job/fire-student")
    public Result<Map<String, Object>> fireStudent(@RequestBody Map<String, Object> request) {
        Integer studentId = (Integer) request.get("studentId");
        Integer jobId = (Integer) request.get("jobId");
        Integer operatorId = (Integer) request.get("operatorId");
        if (studentId == null || jobId == null || operatorId == null) {
            return Result.error("参数缺失");
        }
        Map<String, Object> data = jobService.fireStudent(studentId, jobId, operatorId);
        return Result.success("解雇成功", data);
    }

    @GetMapping("/job/check-slot-occupied")
    public Result<Boolean> checkTimeSlotOccupied(@RequestParam int jobId, 
                                                 @RequestParam String date,
                                                 @RequestParam String timeSlot) {
        try {
            boolean isOccupied = jobService.checkTimeSlotOccupied(jobId, date, timeSlot);
            return Result.success(isOccupied);
        } catch (Exception e) {
            return Result.error("检查时间段占用失败: " + e.getMessage());
        }
    }

    @GetMapping("/job/history-students")
    public Result<List<Map<String, Object>>> getHistoryStudents(@RequestParam int merchantId) {
        return Result.success(jobService.getHistoryStudentsByMerchant(merchantId));
    }

    @GetMapping("/job/student-accepted-jobs")
    public Result<List<Map<String, Object>>> getStudentAcceptedJobs(@RequestParam int studentId) {
        return Result.success(jobService.getStudentAcceptedJobs(studentId));
    }

    @PostMapping("/job/student-resign")
    public Result<Map<String, Object>> studentResign(@RequestBody Map<String, Object> request) {
        Integer studentId = (Integer) request.get("studentId");
        Integer jobId = (Integer) request.get("jobId");
        if (studentId == null || jobId == null) {
            return Result.error("参数缺失");
        }
        return Result.success(jobService.studentResign(studentId, jobId));
    }

    @PostMapping("/job/approve-resign")
    public Result<Map<String, Object>> approveStudentResign(@RequestBody Map<String, Object> request) {
        Integer studentId = (Integer) request.get("studentId");
        Integer jobId = (Integer) request.get("jobId");
        Integer merchantId = (Integer) request.get("merchantId");
        if (studentId == null || jobId == null || merchantId == null) {
            return Result.error("参数缺失");
        }
        return Result.success(jobService.approveStudentResign(studentId, jobId, merchantId));

    }
}
