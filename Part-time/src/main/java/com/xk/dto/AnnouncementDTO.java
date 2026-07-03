package com.xk.dto;

import lombok.Data;

import java.util.Date;

@Data
public class AnnouncementDTO {
    private Integer id;
    private String title;
    private String content;
    private Integer userId;
    private Boolean isPinned;
    private Integer priority;
    private Integer status;
    private Date publishedAt;
    private Date createdAt;
    private Date updatedAt;
}