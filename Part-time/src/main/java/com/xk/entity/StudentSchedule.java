package com.xk.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentSchedule {
    private Integer id;
    private Integer studentId;
    private Integer dayOfWeek; // 1-7，周一到周日
    private String timeSlot; // morning, afternoon, evening
    private Integer isAvailable; // 0-不可用，1-可用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
