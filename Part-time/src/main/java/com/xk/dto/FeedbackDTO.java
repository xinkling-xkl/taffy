package com.xk.dto;

import lombok.Data;

import java.util.Date;
@Data
public class FeedbackDTO {
    private Integer id;
    private Integer userId;
    private String content;
    private String reply;
    private String image;
    private String status;
    private Date data;


}
