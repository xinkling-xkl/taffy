package com.xk.mapper;

import com.xk.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface JobMapper {
    int insert(Job work);
    int deleteById(@Param("id") int id);
    int update(Job work);
    Job selectById(@Param("id") int id);
    List<Job> selectAll();
    List<Job> selectByUserId(@Param("userId") int userId);
    List<Job> selectAvailable();

    int insertMerchantRequirement(@Param("jobId") int jobId, @Param("merchantId") int merchantId,
                                  @Param("dayOfWeek") int dayOfWeek, @Param("timeSlot") String timeSlot,
                                  @Param("neededCount") int neededCount);

    int deleteMerchantRequirementByJobId(@Param("jobId") int jobId);
    int deleteWorkAssignmentsByJobId(@Param("jobId") int jobId);

    // 更新需求人数
    int updateMerchantRequirementFilledCount(@Param("jobId") int jobId,
                                             @Param("dayOfWeek") int dayOfWeek,
                                             @Param("timeSlot") String timeSlot,
                                             @Param("increment") int increment);

    // 新增：更新兼职状态
    int updateJobStatus(@Param("jobId") int jobId, @Param("status") String status);
    // 插入需求时指定 filled_count
    int insertMerchantRequirementWithFilled(
            @Param("jobId") int jobId,
            @Param("merchantId") int merchantId,
            @Param("dayOfWeek") int dayOfWeek,
            @Param("timeSlot") String timeSlot,
            @Param("neededCount") int neededCount,
            @Param("filledCount") int filledCount);

    List<Job> selectByDayAndSlot(@Param("dayOfWeek") int dayOfWeek, @Param("timeSlot") String timeSlot);

    List<Job> selectByDay(@Param("dayOfWeek") int dayOfWeek);
}