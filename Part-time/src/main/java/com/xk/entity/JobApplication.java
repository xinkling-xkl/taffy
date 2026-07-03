package com.xk.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class JobApplication {
    private Integer id;
    private Integer jobId;
    private Integer studentId;
    private Integer merchantId;
    private String studentName;
    private String timeAvailability;
    private Integer creditScore;
    private String applicationContent;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message; // 商户留言或拒绝原因
    private String selectedDates;  // JSON String，存储日期列表

}
