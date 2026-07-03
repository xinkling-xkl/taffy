package com.xk.controller;

import com.xk.common.Result;
import com.xk.dto.IdentityDTO;
import com.xk.dto.UserDTO;
import com.xk.entity.Identity;
import com.xk.exception.BusinessException;
import com.xk.service.IdentityService;
import com.xk.service.MessageService;
import com.xk.service.UserService;
import com.xk.entity.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/identity")
public class IdentityController {

    @Autowired
    private IdentityService identityService;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @PostMapping
    public Result<?> addIdentity(@RequestBody IdentityDTO identityDTO) {
        if (identityDTO.getUserId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (identityDTO.getImageurl() == null || identityDTO.getImageurl().isEmpty()) {
            throw new BusinessException("认证图片不能为空");
        }
        identityService.addIdentity(identityDTO);
        return Result.successMsg("认证申请提交成功");
    }

    @GetMapping("/user/{userId}")
    public Result<List<Map<String, Object>>> getIdentityByUserId(@PathVariable("userId") int userId) {
        List<Identity> identities = identityService.getIdentityByUserId(userId);
        return Result.success(processIdentities(identities));
    }

    @GetMapping
    public Result<List<Map<String, Object>>> getAllIdentity() {
        List<Identity> identities = identityService.getAllIdentity();
        return Result.success(processIdentities(identities));
    }

    @PutMapping("/{id}")
    public Result<?> updateIdentity(@PathVariable("id") int id, @RequestBody Map<String, Object> updates) {
        Identity identity = identityService.getIdentityById(id);
        if (identity == null) {
            throw new BusinessException("认证申请不存在");
        }
        String status = (String) updates.get("status");
        String respond = (String) updates.get("respond");
        if (status == null) {
            throw new BusinessException("状态不能为空");
        }
        if ("已驳回".equals(status) && (respond == null || respond.isEmpty())) {
            throw new BusinessException("驳回原因不能为空");
        }
        identity.setStatus(status);
        identity.setRespond(respond);
        identityService.updateIdentity(identity);

        int userId = identity.getUserId();
        if ("已批准".equals(status)) {
            userService.updateUserIdentity(userId, "商户");
            sendMessage(userId, "您的商户认证申请已通过审核，您现在可以重新登陆使用商户功能了。", "认证通过");
        } else if ("已驳回".equals(status)) {
            sendMessage(userId, "您的商户认证申请已被驳回。驳回原因：" + (respond != null ? respond : "无"), "认证驳回");
        }
        return Result.successMsg("处理认证申请成功");
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteIdentity(@PathVariable("id") int id) {
        Identity identity = identityService.getIdentityById(id);
        if (identity == null) {
            throw new BusinessException("认证申请不存在");
        }
        identityService.deleteIdentity(id);
        return Result.successMsg("认证申请删除成功");
    }

    private void sendMessage(int userId, String content, String title) {
        try {
            Message msg = new Message();
            msg.setSenderId(userId);
            msg.setReceiverId(userId);
            msg.setType("system");
            msg.setContent(content);
            msg.setCreatedAt(java.time.LocalDateTime.now());
            messageService.sendMessage(msg);
        } catch (Exception e) {
            // 消息发送失败不影响主流程
            System.err.println("发送认证通知消息失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> processIdentities(List<Identity> identities) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Identity identity : identities) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", identity.getId());
            item.put("userId", identity.getUserId());
            item.put("imageurl", identity.getImageurl());
            item.put("bname", identity.getBname());
            item.put("respond", identity.getRespond());
            item.put("status", identity.getStatus());
            item.put("data", identity.getData());

            UserDTO user = userService.getUserDTOById(identity.getUserId());
            if (user != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("name", user.getName());
                userInfo.put("phone", user.getPhone());
                userInfo.put("rname", user.getRname());
                userInfo.put("idcard", user.getIdcard());
                item.put("user", userInfo);
            }
            result.add(item);
        }
        return result;
    }
}
