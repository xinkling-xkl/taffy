package com.xk.service.impl;

import com.xk.mapper.SystemConfigMapper;
import com.xk.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Override
    public String getConfig(String key) {
        return systemConfigMapper.selectByKey(key);
    }

    @Override
    public void setConfig(String key, String value) {
        systemConfigMapper.upsert(key, value);
    }
}