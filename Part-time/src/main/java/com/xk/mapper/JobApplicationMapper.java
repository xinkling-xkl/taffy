package com.xk.mapper;

import com.xk.entity.JobApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface JobApplicationMapper {
    List<JobApplication> getApplicationsByJobId(@Param("jobId") int jobId);
    List<JobApplication> getApplicationsByStudentId(@Param("studentId") int studentId);
    List<JobApplication> getApplicationsByMerchantId(@Param("merchantId") int merchantId);
    int insertApplication(JobApplication application);
    int updateApplication(JobApplication application);
    int updateApplicationStatus(@Param("id") int id, @Param("status") String status, @Param("message") String message);
    JobApplication getApplicationById(@Param("id") int id);
    JobApplication getApplicationByJobAndStudent(@Param("jobId") int jobId, @Param("studentId") int studentId);
    List<JobApplication> getApplicationsByJobIdAndStatus(@Param("jobId") int jobId, @Param("status") String status);
    int getAcceptedApplicationsCount(@Param("jobId") int jobId);

}
