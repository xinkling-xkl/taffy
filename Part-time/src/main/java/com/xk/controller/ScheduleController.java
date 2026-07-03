package com.xk.controller;

import com.xk.common.Result;
import com.xk.entity.StudentSchedule;
import com.xk.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    @GetMapping("/student/{studentId}")
    public Result<List<StudentSchedule>> getStudentSchedule(@PathVariable Integer studentId) {
        return Result.success(scheduleService.getStudentSchedule(studentId));
    }

    @PostMapping("/student")
    public Result<?> saveStudentSchedule(@RequestBody List<StudentSchedule> scheduleList) {
        scheduleService.saveStudentSchedule(scheduleList);
        return Result.successMsg("保存成功");
    }

    @GetMapping("/merchant/requirements")
    public Result<List<Map<String, Object>>> getMerchantRequirements(@RequestParam Integer merchantId) {
        return Result.success(scheduleService.getMerchantRequirements(merchantId));
    }
}
