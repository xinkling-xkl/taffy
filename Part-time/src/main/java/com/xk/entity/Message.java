package com.xk.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Message {
    private Integer id;
    private Integer senderId;
    private Integer receiverId;
    private String type;
    private String content;
    private Integer isRead;
    private Integer relatedId;
    private LocalDateTime createdAt;
    private String senderName;
    private String receiverName;
}