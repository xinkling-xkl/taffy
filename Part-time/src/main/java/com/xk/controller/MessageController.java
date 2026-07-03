package com.xk.controller;

import com.xk.common.Result;
import com.xk.entity.Message;
import com.xk.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @GetMapping("/list")
    public Result<List<Message>> getMessageList(@RequestParam int userId) {
        return Result.success(messageService.getMessageList(userId));
    }

    @GetMapping("/unread")
    public Result<List<Message>> getUnreadMessageList(@RequestParam int userId) {
        return Result.success(messageService.getUnreadMessageList(userId));
    }

    @GetMapping("/count")
    public Result<Integer> getUnreadCount(@RequestParam int userId) {
        return Result.success(messageService.getUnreadCount(userId));
    }

    @PostMapping("/send")
    public Result<?> sendMessage(@RequestBody Message message) {
        String res = messageService.sendMessage(message);
        return "success".equals(res) ? Result.successMsg("发送成功") : Result.error(res);
    }

    @PostMapping("/read")
    public Result<?> markAsRead(@RequestParam int id) {
        String res = messageService.markAsRead(id);
        return "success".equals(res) ? Result.successMsg("标记成功") : Result.error(res);
    }

    @PostMapping("/readAll")
    public Result<?> markAllAsRead(@RequestParam int userId) {
        String res = messageService.markAllAsRead(userId);
        return "success".equals(res) ? Result.successMsg("标记成功") : Result.error(res);
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteMessage(@PathVariable int id) {
        String res = messageService.deleteMessage(id);
        return "success".equals(res) ? Result.successMsg("删除成功") : Result.error(res);
    }

    @GetMapping("/{id}")
    public Result<Message> getMessageById(@PathVariable int id) {
        Message message = messageService.getMessageById(id);
        return message != null ? Result.success(message) : Result.error("消息不存在");
    }
}
