package com.xk.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class WorkAssignment {
    private Integer id;
    private Integer jobId;
    private Integer studentId;
    private LocalDate workDate;
    private String timeSlot;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
