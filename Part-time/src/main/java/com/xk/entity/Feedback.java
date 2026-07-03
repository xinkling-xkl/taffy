package com.xk.entity;

import lombok.Data;

import java.util.Date;
@Data
public class Feedback {
    private int id;
    private int userId;
    private String content;
    private String reply;
    private String image;
    private String status;
    private Date data;


}
