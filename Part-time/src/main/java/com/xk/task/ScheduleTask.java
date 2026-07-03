package com.xk.task;

import com.xk.entity.Message;
import com.xk.entity.User;
import com.xk.mapper.ScheduleMapper;
import com.xk.mapper.UserMapper;
import com.xk.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ScheduleTask {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ScheduleMapper scheduleMapper;

    @Autowired
    private MessageService messageService;

    @Transactional
    @Scheduled(cron = "0 0 0 ? * MON")
    public void resetStudentTimePreferences() {
        System.out.println("========== [定时任务] 开始清空学生时间偏好 ==========");
        List<User> students = userMapper.getAllStudents();
        System.out.println("从数据库获取到 " + students.size() + " 名学生");

        int successCount = 0;
        for (User student : students) {
            student.setTimepreference(null);
            userMapper.updateUser(student);

            for (int day = 1; day <= 7; day++) {
                for (String slot : new String[]{"morning", "afternoon", "evening"}) {
                    scheduleMapper.updateStudentSchedule(student.getId(), day, slot, 0);
                }
            }

            Message message = new Message();
            message.setSenderId(student.getId());
            message.setReceiverId(student.getId());
            message.setType("system");
            message.setContent("新的一周即将开始，您的课表可能已更新，请重新登陆账号到个人中心重新设置空闲时间，以便商户在新的一周找到您。");

            try {
                messageService.sendMessage(message);
                successCount++;
            } catch (Exception e) {
                System.err.println("发送提醒消息给学生 " + student.getId() + " 失败: " + e.getMessage());
            }
        }
        System.out.println("========== [定时任务] 清空学生时间偏好完成，成功发送 " + successCount + "/" + students.size() + " 条消息 ==========");
    }
}