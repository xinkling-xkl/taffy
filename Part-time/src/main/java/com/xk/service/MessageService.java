package com.xk.service;

import com.xk.entity.Message;
import java.util.List;

public interface MessageService {
    List<Message> getMessageList(int receiverId);
    List<Message> getUnreadMessageList(int receiverId);
    int getUnreadCount(int receiverId);
    String sendMessage(Message message);
    String markAsRead(int id);
    String markAllAsRead(int receiverId);
    String deleteMessage(int id);
    Message getMessageById(int id);
    void updateMessageContent(int id, String content);
    Message findResignRequestMessage(int senderId, int receiverId, String type, int relatedId);
}