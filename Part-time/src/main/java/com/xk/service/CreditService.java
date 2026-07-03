package com.xk.service;

import com.xk.dto.CreditRecordDTO;
import java.util.List;

public interface CreditService {
    // 三参数方法（保留，用于无特定类型的加分）
    void addCredit(Integer userId, Integer points, String reason);

    // 新增四参数方法（用于标记行为类型，如 complete_job / praise / criticize / punctual 等）
    void addCredit(Integer userId, Integer points, String reason, String actionType);

    Integer getCreditScore(Integer userId);
    List<CreditRecordDTO> getCreditRecordsByUserId(Integer userId);
    String updateCreditScore(Integer userId, Integer points, String reason, String type);
    boolean checkCreditPermission(Integer userId);
    String handleAppeal(Integer userId);
}