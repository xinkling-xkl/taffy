package com.xk.controller;

import com.xk.common.Result;
import com.xk.exception.BusinessException;
import com.xk.service.AttendanceService;
import com.xk.utils.TypeConversionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @PostMapping("/check-in")
    public Result<?> checkIn(@RequestBody Map<String, Object> request) {
        Integer workAssignmentId = TypeConversionUtils.toInteger(request.get("workAssignmentId"));
        Integer studentId = TypeConversionUtils.toInteger(request.get("studentId"));
        String checkInTime = TypeConversionUtils.toString(request.get("checkInTime"));
        Boolean isLate = TypeConversionUtils.toBoolean(request.get("isLate"));
        Integer lateMinutes = TypeConversionUtils.toInteger(request.get("lateMinutes"));

        if (workAssignmentId == null || studentId == null) {
            throw new BusinessException("参数缺失");
        }
        String res = attendanceService.checkIn(workAssignmentId, studentId, checkInTime, isLate, lateMinutes);
        return "success".equals(res) ? Result.successMsg("签到成功") : Result.error(res);
    }

    @PostMapping("/check-out")
    public Result<?> checkOut(@RequestBody Map<String, Object> request) {
        Integer workAssignmentId = TypeConversionUtils.toInteger(request.get("workAssignmentId"));
        Integer studentId = TypeConversionUtils.toInteger(request.get("studentId"));
        String checkOutTime = TypeConversionUtils.toString(request.get("checkOutTime"));

        if (workAssignmentId == null || studentId == null) {
            throw new BusinessException("参数缺失");
        }
        String res = attendanceService.checkOut(workAssignmentId, studentId, checkOutTime);
        return "success".equals(res) ? Result.successMsg("签退成功") : Result.error(res);
    }

    @GetMapping("/today")
    public Result<List<Map<String, Object>>> getTodayAssignments(@RequestParam Integer studentId) {
        return Result.success(attendanceService.getTodayAssignments(studentId));
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistoryAttendances(@RequestParam Integer studentId) {
        return Result.success(attendanceService.getHistoryAttendances(studentId));
    }

    @GetMapping("/check-merchant-confirm")
    public Result<Boolean> checkMerchantConfirm(@RequestParam Integer workAssignmentId) {
        return Result.success(attendanceService.checkMerchantConfirm(workAssignmentId));
    }

    @PostMapping("/merchant-confirm")
    public Result<?> merchantConfirm(@RequestBody Map<String, Object> request) {
        Integer workAssignmentId = TypeConversionUtils.toInteger(request.get("workAssignmentId"));
        Integer merchantId = TypeConversionUtils.toInteger(request.get("merchantId"));
        if (workAssignmentId == null || merchantId == null) {
            throw new BusinessException("参数缺失");
        }
        String res = attendanceService.merchantConfirm(workAssignmentId, merchantId);
        return "success".equals(res) ? Result.successMsg("确认成功") : Result.error(res);
    }

    @GetMapping("/merchant/today")
    public Result<List<Map<String, Object>>> getMerchantTodayAssignments(@RequestParam Integer merchantId) {
        return Result.success(attendanceService.getMerchantTodayAssignments(merchantId));
    }

    @GetMapping("/merchant/history")
    public Result<List<Map<String, Object>>> getMerchantHistoryAttendances(@RequestParam Integer merchantId) {
        return Result.success(attendanceService.getMerchantHistoryAttendances(merchantId));
    }

    @PostMapping("/mark-early-leave")
    public Result<?> markEarlyLeave(@RequestBody Map<String, Object> request) {
        Integer workAssignmentId = TypeConversionUtils.toInteger(request.get("workAssignmentId"));
        Integer merchantId = TypeConversionUtils.toInteger(request.get("merchantId"));
        String reason = TypeConversionUtils.toString(request.get("reason"));
        if (workAssignmentId == null || merchantId == null) {
            throw new BusinessException("参数缺失");
        }
        String res = attendanceService.markEarlyLeave(workAssignmentId, merchantId, reason);
        return "success".equals(res) ? Result.successMsg("标记提前下班成功") : Result.error(res);
    }

    @PostMapping("/mark-late")
    public Result<?> markLate(@RequestBody Map<String, Object> request) {
        Integer workAssignmentId = TypeConversionUtils.toInteger(request.get("workAssignmentId"));
        Integer merchantId = TypeConversionUtils.toInteger(request.get("merchantId"));
        String reason = TypeConversionUtils.toString(request.get("reason"));
        if (workAssignmentId == null || merchantId == null) {
            throw new BusinessException("参数缺失");
        }
        String res = attendanceService.markLate(workAssignmentId, merchantId, reason);
        return "success".equals(res) ? Result.successMsg("标记迟到成功") : Result.error(res);
    }

    @PostMapping("/mark-checked-in")
    public Result<?> markCheckedIn(@RequestBody Map<String, Object> request) {
        Integer workAssignmentId = TypeConversionUtils.toInteger(request.get("workAssignmentId"));
        Integer merchantId = TypeConversionUtils.toInteger(request.get("merchantId"));
        String reason = TypeConversionUtils.toString(request.get("reason"));
        if (workAssignmentId == null || merchantId == null) {
            throw new BusinessException("参数缺失");
        }
        String res = attendanceService.markCheckedIn(workAssignmentId, merchantId, reason);
        return "success".equals(res) ? Result.successMsg("标记为签到成功") : Result.error(res);
    }

    @PostMapping("/mark-absent")
    public Result<?> markAbsent(@RequestBody Map<String, Object> request) {
        Integer workAssignmentId = TypeConversionUtils.toInteger(request.get("workAssignmentId"));
        Integer merchantId = TypeConversionUtils.toInteger(request.get("merchantId"));
        String reason = TypeConversionUtils.toString(request.get("reason"));
        if (workAssignmentId == null || merchantId == null) {
            throw new BusinessException("参数缺失");
        }
        String res = attendanceService.markAbsent(workAssignmentId, merchantId, reason);
        return "success".equals(res) ? Result.successMsg("标记缺勤成功") : Result.error(res);
    }

    @PostMapping("/mark-normal-checked-in")
    public Result<?> markNormalCheckedIn(@RequestBody Map<String, Object> request) {
        Integer workAssignmentId = TypeConversionUtils.toInteger(request.get("workAssignmentId"));
        Integer merchantId = TypeConversionUtils.toInteger(request.get("merchantId"));
        if (workAssignmentId == null || merchantId == null) {
            throw new BusinessException("参数缺失");
        }
        String res = attendanceService.markNormalCheckedIn(workAssignmentId, merchantId);
        return "success".equals(res) ? Result.successMsg("已恢复为正常签到") : Result.error(res);
    }

    @PostMapping("/merchant-check-out")
    public Result<?> merchantCheckOut(@RequestBody Map<String, Object> request) {
        Integer workAssignmentId = TypeConversionUtils.toInteger(request.get("workAssignmentId"));
        Integer merchantId = TypeConversionUtils.toInteger(request.get("merchantId"));
        String checkOutTime = TypeConversionUtils.toString(request.get("checkOutTime"));
        if (workAssignmentId == null || merchantId == null) {
            throw new BusinessException("参数缺失");
        }
        String res = attendanceService.merchantCheckOut(workAssignmentId, merchantId, checkOutTime);
        return "success".equals(res) ? Result.successMsg("商户签退成功") : Result.error(res);
    }
}
