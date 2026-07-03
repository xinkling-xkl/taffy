package com.xk.entity;

import lombok.Data;
import java.util.Date;

@Data
public class User {
    private int id;
    private String name;
    private String rname;
    private String number;
    private String idcard;
    private int age;
    private String phone;
    private String image;
    private String password;
    private String identity;
    private Integer credit;
    private Integer isworking; // 0=未工作，1=工作中（商户可为null）
    private String timepreference;
    private Integer violationCount; // 违规次数
    private Integer isPenalty; // 是否被处罚：0-正常，1-处罚中
    private Date penaltyEndTime; // 处罚结束时间
    private Integer isAppeal; // 是否申诉：0-无，1-申诉中
    private Integer isRectification; // 是否整改：0-无需，1-整改中
    private Date rectificationApplyTime; // 整改申请时间
    private String rectificationStatus;
    private Date createdAt;  // 注册时间


}
