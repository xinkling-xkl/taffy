package com.xk.service;

import java.util.Set;

public interface CacheClearService {

    /** 清除学生可用时段缓存 */
    void clearStudentCache(int studentId);

    /** 清除兼职空缺时段缓存 */
    void clearJobCache(int jobId);

    /** 获取学生可用时段（如果缓存不存在则计算并存入） */
    Set<String> getStudentAvailableSlots(int studentId);

    /** 获取兼职空缺时段（如果缓存不存在则计算并存入） */
    Set<String> getJobAvailableSlots(int jobId);
}