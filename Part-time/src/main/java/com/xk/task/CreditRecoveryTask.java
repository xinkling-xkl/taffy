package com.xk.task;

import com.xk.entity.User;
import com.xk.service.CreditService;
import com.xk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CreditRecoveryTask {

    @Autowired
    private UserService userService;

    @Autowired
    private CreditService creditService;

    /**
     * 每天凌晨 2 点执行，为信用分低于 50 且被处罚的用户增加 1 分
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void recoverCreditDaily() {
        List<User> penaltyUsers = userService.getPenaltyUsers();
        for (User user : penaltyUsers) {
            if (user.getCredit() != null && user.getCredit() < 50) {
                System.out.println("========== [定时任务] 开始执行信用恢复 ==========");
                creditService.addCredit(user.getId(), 1, "每日自动恢复", "daily_recover");
            }
        }
    }
}