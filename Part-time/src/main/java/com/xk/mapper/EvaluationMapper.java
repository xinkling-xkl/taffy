package com.xk.mapper;

import org.apache.ibatis.annotations.Param;
import com.xk.entity.Evaluation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface EvaluationMapper {
    List<Map<String, Object>> getPendingEvaluations(Integer userId);
    List<Map<String, Object>> getHistoryEvaluations(Integer userId);
    Map<String, Object> getEvaluationByAssignmentId(Integer workAssignmentId);

    // 修改为接收实体对象
    int insertEvaluation(Evaluation evaluation);

    List<Map<String, Object>> getEvaluationsByJobId(@Param("jobId") int jobId);
    int updateEvaluation(@Param("evaluationId") int evaluationId,
                         @Param("rating") int rating,
                         @Param("comment") String comment);
    int deleteEvaluation(@Param("evaluationId") int evaluationId);
    int replyEvaluation(@Param("evaluationId") int evaluationId,
                        @Param("reply") String reply,
                        @Param("replyTime") LocalDateTime replyTime);

    Map<String, Object> getEvaluationById(@Param("evaluationId") Integer evaluationId);
    int updateEvaluationIsPositive(@Param("evaluationId") int evaluationId,
                                   @Param("isPositive") boolean isPositive);
    // 获取商户待评价的工作分配
    List<Map<String, Object>> getPendingMerchantEvaluations(@Param("merchantId") int merchantId);

    // 获取学生对学生的所有评价（商户查看学生时使用）
    List<Map<String, Object>> getMerchantEvaluationsForStudent(@Param("studentId") int studentId);

    // 插入待评价记录（签退时生成）
    int insertPendingEvaluation(@Param("evaluatorId") int evaluatorId,
                                @Param("evaluatedId") int evaluatedId,
                                @Param("jobId") int jobId,
                                @Param("workAssignmentId") int workAssignmentId,
                                @Param("type") String type);


    int updateEvaluationWithOldRating(@Param("evaluationId") int evaluationId,
                                      @Param("rating") int rating,
                                      @Param("comment") String comment,
                                      @Param("oldRating") int oldRating);
    // 文件：com.xk.mapper.EvaluationMapper.java

    Map<String, Object> getMerchantEvaluationByAssignmentId(@Param("workAssignmentId") int workAssignmentId);
}