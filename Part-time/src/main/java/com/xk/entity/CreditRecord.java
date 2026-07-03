package com.xk.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreditRecord {
    private Integer id;
    private Integer userId;
    private Integer changeScore;
    private String reason;
    private String actionType;
    private Integer creditBefore;
    private Integer creditAfter;
    private String creditLevel;
    private LocalDateTime createdAt;
}
