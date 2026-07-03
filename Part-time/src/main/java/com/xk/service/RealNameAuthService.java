package com.xk.service;

import com.xk.dto.RealNameAuthDTO;
import java.util.Map;

public interface RealNameAuthService {
    /**
     * 学生实名认证
     * @param userId 用户ID
     * @param authDTO 认证信息
     * @return 认证结果
     */
    Map<String, Object> verifyStudent(int userId, RealNameAuthDTO authDTO);

    /**
     * 商户实名认证
     * @param userId 用户ID
     * @param authDTO 认证信息
     * @return 认证结果
     */
    Map<String, Object> verifyMerchant(int userId, RealNameAuthDTO authDTO);

    /**
     * 验证身份证号格式
     * @param idcard 身份证号
     * @return 是否有效
     */
    boolean isValidIdCard(String idcard);
}
