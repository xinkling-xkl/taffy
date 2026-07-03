package com.xk.dto;

import lombok.Data;

import java.util.Date;

@Data
public class IdentityDTO {
    private Integer id;
    private Integer userId;
    private String imageurl;
    private String bname;      // 商户名称
    private String respond;
    private String status;
    private Date data;
}
