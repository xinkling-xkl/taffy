package com.xk.controller;

import com.xk.common.Result;
import com.xk.dto.RealNameAuthDTO;
import com.xk.service.RealNameAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/realname-auth")
public class RealNameAuthController {

    @Autowired
    private RealNameAuthService realNameAuthService;

    @PostMapping("/student")
    public Result<Map<String, Object>> verifyStudent(@RequestParam("userId") int userId, @RequestBody RealNameAuthDTO authDTO) {
        return Result.success(realNameAuthService.verifyStudent(userId, authDTO));
    }

    @PostMapping("/merchant")
    public Result<Map<String, Object>> verifyMerchant(@RequestParam("userId") int userId, @RequestBody RealNameAuthDTO authDTO) {
        return Result.success(realNameAuthService.verifyMerchant(userId, authDTO));
    }
}
