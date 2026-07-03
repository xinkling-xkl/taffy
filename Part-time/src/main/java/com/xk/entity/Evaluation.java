package com.xk.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Evaluation {
    private Integer id;
    private Integer evaluatorId;
    private Integer evaluatedId;
    private Integer jobId;
    private Integer workAssignmentId;   // 新增
    private Integer rating;
    private Boolean isPositive;         // 新增
    private String comment;
    private String type;
    private Integer creditImpact;
    private String reply;               // 新增
    private LocalDateTime replyTime;    // 新增
    private LocalDateTime createdAt;
}