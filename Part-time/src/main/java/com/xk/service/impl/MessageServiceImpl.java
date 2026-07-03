package com.xk.service.impl;

import com.xk.entity.Message;
import com.xk.mapper.MessageMapper;
import com.xk.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private MessageMapper messageMapper;

    @Override
    public List<Message> getMessageList(int receiverId) {
        return messageMapper.getMessageList(receiverId);
    }

    @Override
    public List<Message> getUnreadMessageList(int receiverId) {
        return messageMapper.getUnreadMessageList(receiverId);
    }

    @Override
    public int getUnreadCount(int receiverId) {
        return messageMapper.getUnreadCount(receiverId);
    }

    @Override
    public String sendMessage(Message message) {
        try {
            // 检查是否是联系学生的消息
            if ("contact_student".equals(message.getType())) {
                // 检查最近5分钟内是否已经联系过该学生
                List<Message> recentContacts = messageMapper.getRecentContacts(
                    message.getSenderId(), 
                    message.getReceiverId(), 
                    5 // 5分钟内
                );
                
                if (recentContacts != null && !recentContacts.isEmpty()) {
                    return "您最近5分钟内已经联系过该学生，请稍后再试";
                }
            }
            
            messageMapper.insertMessage(message);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "发送消息失败: " + e.getMessage();
        }
    }

    @Override
    public String markAsRead(int id) {
        try {
            messageMapper.markAsRead(id);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "标记已读失败: " + e.getMessage();
        }
    }

    @Override
    public String markAllAsRead(int receiverId) {
        try {
            messageMapper.markAllAsRead(receiverId);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "标记全部已读失败: " + e.getMessage();
        }
    }

    @Override
    public String deleteMessage(int id) {
        try {
            messageMapper.deleteMessage(id);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "删除消息失败: " + e.getMessage();
        }
    }

    @Override
    public Message getMessageById(int id) {
        return messageMapper.getMessageById(id);
    }

    @Override
    public void updateMessageContent(int id, String content) {
        messageMapper.updateMessageContent(id, content);
    }

    @Override
    public Message findResignRequestMessage(int senderId, int receiverId, String type, int relatedId) {
        return messageMapper.findResignRequestMessage(senderId, receiverId, type, relatedId);
    }
}