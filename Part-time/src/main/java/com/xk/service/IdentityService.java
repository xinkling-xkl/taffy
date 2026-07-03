package com.xk.service;

import com.xk.dto.IdentityDTO;
import com.xk.entity.Identity;

import java.util.List;

public interface IdentityService {
    /**
     * 创建认证申请
     * @param identity
     */
    void addIdentity(IdentityDTO identity);
    
    /**
     * 删除认证申请
     * @param id
     * @return
     */
    boolean deleteIdentity(int id);
    
    /**
     * 更新认证申请
     * @param identity
     * @return
     */
    boolean updateIdentity(Identity identity);
    
    /**
     * 根据ID获取认证申请
     * @param id
     * @return
     */
    Identity getIdentityById(int id);
    
    /**
     * 根据用户ID获取认证申请
     * @param userId
     * @return
     */
    List<Identity> getIdentityByUserId(int userId);
    
    /**
     * 获取所有认证申请
     * @return
     */
    List<Identity> getAllIdentity();
}
