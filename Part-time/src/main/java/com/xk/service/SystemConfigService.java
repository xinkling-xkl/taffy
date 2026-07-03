package com.xk.service;

public interface SystemConfigService {
    String getConfig(String key);
    void setConfig(String key, String value);
}
