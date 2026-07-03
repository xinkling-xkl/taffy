package com.xk.controller;

import com.xk.common.Result;
import com.xk.service.AssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignment")
public class AssignmentController {

    @Autowired
    private AssignmentService assignmentService;

    @GetMapping("/student/{studentId}")
    public Result<List<Map<String, Object>>> getStudentAssignments(@PathVariable String studentId) {
        Integer id = Integer.parseInt(studentId);
        return Result.success(assignmentService.getStudentAssignments(id));
    }

    @GetMapping("/merchant")
    public Result<List<Map<String, Object>>> getMerchantAssignments(@RequestParam Integer merchantId) {
        return Result.success(assignmentService.getMerchantAssignments(merchantId));
    }

    @PostMapping("/confirm")
    public Result<?> confirmAssignment(@RequestBody Map<String, Object> request) {
        Integer assignmentId = com.xk.utils.TypeConversionUtils.toInteger(request.get("assignmentId"));
        String res = assignmentService.confirmAssignment(assignmentId);
        return "success".equals(res) ? Result.successMsg("确认成功") : Result.error(res);
    }

    @PostMapping("/invite")
    public Result<?> inviteStudent(@RequestBody Map<String, Object> request) {
        Integer studentId = com.xk.utils.TypeConversionUtils.toInteger(request.get("studentId"));
        Integer jobId = com.xk.utils.TypeConversionUtils.toInteger(request.get("jobId"));
        Integer merchantRequirementId = com.xk.utils.TypeConversionUtils.toInteger(request.get("merchantRequirementId"));
        String timeSlot = com.xk.utils.TypeConversionUtils.toString(request.get("timeSlot"));
        Integer dayOfWeek = com.xk.utils.TypeConversionUtils.toInteger(request.get("dayOfWeek"));

        String res = assignmentService.inviteStudent(studentId, jobId, merchantRequirementId, timeSlot, dayOfWeek);
        return "success".equals(res) ? Result.successMsg("邀请成功") : Result.error(res);
    }
}
