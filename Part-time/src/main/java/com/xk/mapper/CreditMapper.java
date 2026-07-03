package com.xk.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CreditMapper {
    // 新插入方法，包含完整信用记录字段
    void insertCreditRecord(@Param("userId") Integer userId,
                            @Param("changeScore") Integer points,
                            @Param("reason") String reason,
                            @Param("actionType") String actionType,
                            @Param("creditBefore") Integer creditBefore,
                            @Param("creditAfter") Integer creditAfter,
                            @Param("creditLevel") String creditLevel,
                            @Param("createdAt") LocalDateTime createdAt);

    Integer getCreditScore(Integer userId);
    void updateUserCredit(@Param("userId") Integer userId, @Param("credit") Integer credit);
    List<Map<String, Object>> getCreditRecordsByUserId(Integer userId);
}