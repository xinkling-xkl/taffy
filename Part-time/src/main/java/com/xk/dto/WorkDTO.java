package com.xk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkDTO {
    private Integer id;
    private String title;
    private String content;
    private String address;
    private String phone;
    private String imageUrl;
    private Integer userId;
    private String userName;
    private String status;
    private String createTime;
    private String salary;
    private String time;
    private String remunerationType;
    private String category;
    private String recruitmentLimit;
    private String applicationCount;
    private String tags;
    private List<Map<String, Object>> requirements;
}
