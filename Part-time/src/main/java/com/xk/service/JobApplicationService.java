package com.xk.service;

import com.xk.dto.JobApplicationDTO;
import com.xk.entity.JobApplication;
import java.util.List;

public interface JobApplicationService {
    String createApplication(JobApplication application);
    List<JobApplicationDTO> getApplicationsByJobId(int jobId);
    List<JobApplicationDTO> getApplicationsByStudentId(int studentId);
    List<JobApplicationDTO> getApplicationsByMerchantId(int merchantId);
    String updateApplicationStatus(int id, String status);
    String updateApplicationStatus(int id, String status, String message);
    String updateApplicationStatus(int id, String status, String message, boolean forceAccept);
    JobApplicationDTO getApplicationById(int id);
    int getAcceptedApplicationsCount(int jobId);
    boolean hasAcceptedApplication(int jobId, int studentId);
}
