package com.xk.controller;

import com.xk.common.Result;
import com.xk.dto.CreditRecordDTO;
import com.xk.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit")
public class CreditController {

    @Autowired
    private CreditService creditService;

    @GetMapping("/records")
    public Result<List<CreditRecordDTO>> getCreditRecords(@RequestParam int userId) {
        return Result.success(creditService.getCreditRecordsByUserId(userId));
    }

    @PostMapping("/update")
    public Result<?> updateCreditScore(
            @RequestParam int userId,
            @RequestParam int changeScore,
            @RequestParam String reason,
            @RequestParam String actionType) {
        creditService.updateCreditScore(userId, changeScore, reason, actionType);
        return Result.successMsg("更新成功");
    }

    @GetMapping("/check")
    public Result<Boolean> checkCreditPermission(@RequestParam int userId) {
        return Result.success(creditService.checkCreditPermission(userId));
    }

    @PostMapping("/appeal")
    public Result<?> appeal(@RequestParam int userId) {
        creditService.handleAppeal(userId);
        return Result.successMsg("提交申诉成功");
    }
}
