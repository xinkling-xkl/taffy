package com.xk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkAssignmentMapper {
    
    /**
     * 获取商户今日的工作分配
     */
    List<Map<String, Object>> getMerchantTodayAssignments(@Param("merchantId") Integer merchantId);
    
    /**
     * 获取商户历史工作分配
     */
    List<Map<String, Object>> getMerchantHistoryAssignments(@Param("merchantId") Integer merchantId);
    
    /**
     * 根据ID获取工作分配
     */
    Map<String, Object> getWorkAssignmentById(@Param("id") Integer id);
    
    /**
     * 创建工作分配
     */
    int createWorkAssignment(@Param("studentId") Integer studentId, 
                           @Param("jobId") Integer jobId,
                           @Param("merchantId") Integer merchantId,
                           @Param("workDate") String workDate,
                           @Param("timeSlot") String timeSlot,
                           @Param("status") String status);
    
    /**
     * 更新工作分配状态
     */
    int updateWorkAssignmentStatus(@Param("id") Integer id, @Param("status") String status);
    
    /**
     * 获取学生的工作分配
     */
    List<Map<String, Object>> getStudentAssignments(@Param("studentId") Integer studentId);
    // WorkAssignmentMapper
    List<Integer> getStudentIdsByDateAndSlot(@Param("workDate") LocalDate workDate, @Param("timeSlot") String timeSlot);
    // WorkAssignmentMapper.java 新增方法
    /**
     * 获取某学生在某兼职下的所有活跃工作分配
     */
    List<Map<String, Object>> getActiveAssignmentsByJobIdAndStudent(
            @Param("jobId") int jobId,
            @Param("studentId") int studentId
    );
    /**
     * 获取某个兼职下所有活跃的工作分配（未取消、未完成）
     */
    List<Map<String, Object>> getActiveAssignmentsByJobId(@Param("jobId") int jobId);
    List<Map<String, Object>> getActiveAssignmentsByStudentAndJob(
            @Param("studentId") int studentId,
            @Param("jobId") int jobId,
            @Param("workDate") LocalDate workDate,
            @Param("timeSlot") String timeSlot
    );

    List<Map<String, Object>> getHistoryAssignmentsByMerchant(int merchantId);

    List<Map<String, Object>> getStudentAcceptedJobs(@Param("studentId") int studentId);
    /**
     * 获取学生未来被占用的时段（已分配且未完成的工作）
     * @param studentId 学生ID
     * @param fromDate 起始日期（通常为今天）
     * @return 占用时段列表，每个元素包含 work_date 和 time_slot
     */
    List<Map<String, Object>> getOccupiedSlotsForStudent(@Param("studentId") int studentId,
                                                         @Param("fromDate") LocalDate fromDate);


    // WorkAssignmentMapper.java
    List<Map<String, Object>> getActiveAssignmentsByDayAndSlot(@Param("studentId") int studentId,
                                                               @Param("dayOfWeek") int dayOfWeek,
                                                               @Param("timeSlot") String timeSlot);


}