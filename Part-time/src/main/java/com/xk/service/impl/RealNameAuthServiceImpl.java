package com.xk.service.impl;

import com.xk.dto.RealNameAuthDTO;
import com.xk.entity.User;
import com.xk.mapper.UserMapper;
import com.xk.service.RealNameAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class RealNameAuthServiceImpl implements RealNameAuthService {

    @Autowired
    private UserMapper userMapper;

    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");

    @Override
    public Map<String, Object> verifyStudent(int userId, RealNameAuthDTO authDTO) {
        Map<String, Object> result = new HashMap<>();

        if (authDTO.getName() == null || authDTO.getName().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "请输入姓名");
            return result;
        }

        if (authDTO.getNumber() == null || authDTO.getNumber().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "请输入学号");
            return result;
        }

        User user = userMapper.getUserById(userId);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        user.setRname(authDTO.getName());
        user.setNumber(authDTO.getNumber());
        user.setIdentity("学生");
        userMapper.updateUser(user);

        result.put("success", true);
        result.put("message", "学生认证成功");
        return result;
    }

    @Override
    public Map<String, Object> verifyMerchant(int userId, RealNameAuthDTO authDTO) {
        Map<String, Object> result = new HashMap<>();

        if (!isValidIdCard(authDTO.getIdcard())) {
            result.put("success", false);
            result.put("message", "身份证号格式不正确");
            return result;
        }

        User user = userMapper.getUserById(userId);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        user.setRname(authDTO.getName());
        user.setIdcard(authDTO.getIdcard());
        userMapper.updateUser(user);

        result.put("success", true);
        result.put("message", "认证信息已提交，请等待管理员审核");
        return result;
    }

    @Override
    public boolean isValidIdCard(String idcard) {
        if (idcard == null || idcard.length() != 18) {
            return false;
        }
        return ID_CARD_PATTERN.matcher(idcard).matches();
    }
}
