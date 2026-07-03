package com.xk.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EvaluationDTO {
    private Integer id;
    private Integer evaluatorId;
    private Integer evaluatedId;
    private Integer jobId;
    private Integer rating;
    private String comment;
    private String type;
    private Integer creditImpact;
    private LocalDateTime createdAt;
    private String evaluatorName;
    private String evaluatedName;
    private String jobTitle;
}
