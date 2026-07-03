package com.xk.service;

import com.xk.dto.WorkDTO;
import com.xk.entity.Job;

import java.util.List;
import java.util.Map;

public interface JobService {
    int addWork(Job work);
    boolean deleteWork(int id);
    boolean updateWork(Job work);
    Job getWorkById(int id);
    List<Job> getAllWorks();
    List<Job> getWorksByUserId(int userId);
    List<Job> getAvailableWorks();
    WorkDTO addWorkDTO(WorkDTO workDTO);
    boolean acceptWork(int workId, int userId);
    List<Map<String, Object>> getWorkingStudentsByMerchant(int merchantId);
    List<Map<String, Object>> getHistoryStudentsByMerchant(int merchantId);

    List<WorkDTO> getAllWorkDTOs();
    List<WorkDTO> getAvailableWorkDTOs();
    List<WorkDTO> getMyWorkDTOs(int userId);
    WorkDTO getWorkDTOById(int id);
    WorkDTO addWorkWithPublisher(WorkDTO workDTO, int userId);
    void updateWorkWithPermission(Job job, int userId);
    void deleteWorkWithPermission(int jobId, int userId);
    Map<String, Object> applyForJob(int jobId, int userId);
    Map<String, Object> cancelAssignment(int workAssignmentId, int userId);
    Map<String, Object> fireStudent(int studentId, int jobId, int operatorId);
    List<Map<String, Object>> getStudentAcceptedJobs(int studentId);
    Map<String, Object> studentResign(int studentId, int jobId);
    Map<String, Object> approveStudentResign(int studentId, int jobId, int merchantId);
    boolean checkTimeSlotOccupied(int jobId, String date, String timeSlot);
}
