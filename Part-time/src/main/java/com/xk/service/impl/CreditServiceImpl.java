package com.xk.service.impl;

import com.xk.dto.CreditRecordDTO;
import com.xk.entity.Message;
import com.xk.mapper.CreditMapper;
import com.xk.service.CreditService;
import com.xk.service.MessageService;
import com.xk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CreditServiceImpl implements CreditService {

    @Autowired
    private CreditMapper creditMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private MessageService messageService;
    @Override
    public void addCredit(Integer userId, Integer points, String reason) {
        addCredit(userId, points, reason, "unknown");
    }

    @Override
    public void addCredit(Integer userId, Integer points, String reason, String actionType) {
        Integer current = getCreditScore(userId);
        if (current == null) current = 60;
        int after = current + points;

        if (after > 100) {
            points = 100 - current;
            after = 100;
        }
        if (after < 0) {
            points = -current;
            after = 0;
        }

        creditMapper.insertCreditRecord(userId, points, reason, actionType, current, after,
                calculateLevel(after), LocalDateTime.now());
        creditMapper.updateUserCredit(userId, after);

        if (after < 50 && (current >= 50 || current == null)) {
            userService.freezeUser(userId);
        } else if (after >= 50 && current < 50) {
            userService.unfreezeUser(userId);
        }

        // 发送信用变动通知（sender_id 使用 userId，确保外键约束满足）
        Message msg = new Message();
        msg.setSenderId(userId);
        msg.setReceiverId(userId);
        msg.setType("credit_change");
        msg.setContent(String.format("您的信用分变化了 %+d 分（%s），当前信用分：%d", points, reason, after));
        msg.setCreatedAt(LocalDateTime.now());
        messageService.sendMessage(msg);
    }
    @Override
    public Integer getCreditScore(Integer userId) {
        return creditMapper.getCreditScore(userId);
    }

    @Override
    public List<CreditRecordDTO> getCreditRecordsByUserId(Integer userId) {
        List<Map<String, Object>> records = creditMapper.getCreditRecordsByUserId(userId);
        List<CreditRecordDTO> dtos = new ArrayList<>();
        for (Map<String, Object> rec : records) {
            CreditRecordDTO dto = new CreditRecordDTO();
            dto.setId(toInteger(rec.get("id")));
            dto.setUserId(toInteger(rec.get("user_id")));
            dto.setChangeScore(toInteger(rec.get("change_score")));
            dto.setReason(com.xk.utils.TypeConversionUtils.toString(rec.get("reason")));
            dto.setActionType(com.xk.utils.TypeConversionUtils.toString(rec.get("action_type")));
            dto.setCreditBefore(toInteger(rec.get("credit_before")));
            dto.setCreditAfter(toInteger(rec.get("credit_after")));
            dto.setCreditLevel(com.xk.utils.TypeConversionUtils.toString(rec.get("credit_level")));
            
            // 安全处理created_at字段
            Object createdAtObj = rec.get("created_at");
            if (createdAtObj instanceof java.sql.Timestamp) {
                dto.setCreatedAt(((java.sql.Timestamp) createdAtObj).toLocalDateTime());
            } else if (createdAtObj instanceof LocalDateTime) {
                dto.setCreatedAt((LocalDateTime) createdAtObj);
            } else if (createdAtObj instanceof String) {
                dto.setCreatedAt(LocalDateTime.parse(((String) createdAtObj).replace("Z", "")));
            } else {
                dto.setCreatedAt(LocalDateTime.now());
            }
            
            dtos.add(dto);
        }
        return dtos;
    }

    @Override
    public String updateCreditScore(Integer userId, Integer points, String reason, String type) {
        addCredit(userId, points, reason, type);
        return "success";
    }

    @Override
    public boolean checkCreditPermission(Integer userId) {
        Integer credit = getCreditScore(userId);
        return credit != null && credit >= 50;
    }

    @Override
    public String handleAppeal(Integer userId) {
        return "申诉提交成功，等待管理员审核";
    }

    public void processAppeal(int userId, boolean approve) {
        if (approve) {
            creditMapper.updateUserCredit(userId, 60);
            userService.unfreezeUser(userId);
            int newCount = userService.incrementViolationCount(userId);
            if (newCount >= 3) {
                userService.permanentlyBan(userId);
            }
        }
    }

    private String calculateLevel(int credit) {
        if (credit >= 90) return "优秀";
        else if (credit >= 80) return "良好";
        else if (credit >= 60) return "合格";
        else return "危险";
    }

    private Integer toInteger(Object obj) {
        return com.xk.utils.TypeConversionUtils.toInteger(obj);
    }
}