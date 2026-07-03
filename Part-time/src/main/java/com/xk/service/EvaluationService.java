package com.xk.service;

import java.util.List;
import java.util.Map;

public interface EvaluationService {
    List<Map<String, Object>> getPendingEvaluations(Integer userId);
    List<Map<String, Object>> getHistoryEvaluations(Integer userId);
    String submitEvaluation(Integer evaluatorId, Integer evaluatedId, Integer jobId, Integer workAssignmentId, Integer rating, String comment, Boolean isPositive);
    List<Map<String, Object>> getEvaluationsByJobId(Integer jobId);
    String updateEvaluation(Integer evaluationId, Integer rating, String comment, Integer userId);
    String deleteEvaluation(Integer evaluationId, Integer userId);
    String replyEvaluation(Integer evaluationId, Integer merchantId, String reply);
    Map<String, Object> getEvaluationById(Integer evaluationId);

    String submitMerchantEvaluation(Integer evaluatorId, Integer evaluatedId, Integer jobId, Integer workAssignmentId, Integer rating, String comment, Boolean isPositive);
}
