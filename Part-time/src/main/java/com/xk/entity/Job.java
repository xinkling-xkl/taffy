package com.xk.entity;

import lombok.Data;

@Data
public class Job {
    private int id;
    private String title;
    private String content;
    private String address;
    private String phone;
    private String imageUrl;
    private int userId;
    private String status;
    private String createTime;
    private String salary; // 薪资
    private String time; // 工作时间（JSON类型，存储为字符串）
    private String remunerationType; // 结算类型：hourly(小时结), daily(日结), weekly(周结)
    private String category; // 兼职类型：all, work_study, campus_delivery, temporary
    private String recruitmentLimit; // 招聘人数上限
    private String applicationCount; // 申请人数
    private String tags; // 标签（JSON类型，存储为字符串）
}
