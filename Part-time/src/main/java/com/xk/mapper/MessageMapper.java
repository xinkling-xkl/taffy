package com.xk.mapper;

import com.xk.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {
    List<Message> getMessageList(@Param("receiverId") int receiverId);
    List<Message> getUnreadMessageList(@Param("receiverId") int receiverId);
    int getUnreadCount(@Param("receiverId") int receiverId);
    int insertMessage(Message message);
    int markAsRead(@Param("id") int id);
    int markAllAsRead(@Param("receiverId") int receiverId);
    int deleteMessage(@Param("id") int id);
    Message getMessageById(@Param("id") int id);
    List<Message> getRecentContacts(@Param("senderId") int senderId, 
                                   @Param("receiverId") int receiverId, 
                                   @Param("minutes") int minutes);
    Message findResignRequestMessage(@Param("senderId") int senderId,
                                     @Param("receiverId") int receiverId,
                                     @Param("type") String type,
                                     @Param("relatedId") int relatedId);
    int updateMessageContent(@Param("id") int id, @Param("content") String content);
}