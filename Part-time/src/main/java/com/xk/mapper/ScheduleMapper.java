package com.xk.mapper;

import com.xk.entity.StudentSchedule;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ScheduleMapper {
    List<StudentSchedule> getStudentSchedule(Integer studentId);
    void deleteStudentSchedule(Integer studentId, Integer dayOfWeek, String timeSlot);
    void insertStudentSchedule(StudentSchedule schedule);
    List<Map<String, Object>> getMerchantRequirements(Integer merchantId);
    List<Integer> findAvailableStudentIds(@Param("dayOfWeek") int dayOfWeek, @Param("timeSlot") String timeSlot);
    StudentSchedule getStudentScheduleByDayAndSlot(@Param("studentId") Integer studentId, @Param("dayOfWeek") int dayOfWeek, @Param("timeSlot") String timeSlot);
    void updateStudentSchedule(@Param("studentId") Integer studentId, @Param("dayOfWeek") Integer dayOfWeek, @Param("timeSlot") String timeSlot, @Param("isAvailable") Integer isAvailable);

    List<Map<String, Object>> getUnfilledMerchantRequirements(@Param("merchantId") Integer merchantId);

    List<Map<String, Object>> getStudentRemainingFreeSlots(@Param("studentId") Integer studentId);

    List<Integer> findAvailableStudentsForSlot(@Param("dayOfWeek") int dayOfWeek, @Param("timeSlot") String timeSlot);

    List<Map<String, Object>> getStudentsByDayAndSlot(@Param("dayOfWeek") int dayOfWeek, @Param("timeSlot") String timeSlot);

    List<Map<String, Object>> getStudentsByDay(@Param("dayOfWeek") int dayOfWeek);

    List<Integer> findStudentsByTimePreference(@Param("dayOfWeek") int dayOfWeek, @Param("period") int period);

    List<Map<String, Object>> getStudentFreeSlotsFromPreference(@Param("studentId") int studentId);

    List<Map<String, Object>> getStudentsByDayAndSlotPref(@Param("dayOfWeek") int dayOfWeek, @Param("period") int period);

    List<Map<String, Object>> getStudentsByDayPref(@Param("dayOfWeek") int dayOfWeek);
}