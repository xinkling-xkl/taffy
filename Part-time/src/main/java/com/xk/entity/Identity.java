package com.xk.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Identity {
    private int id;
    private int userId;
    private String imageurl;
    private String bname;      // 商户名称
    private String respond;
    private String status;
    private Date data;
}
