package com.xk.controller;

import com.xk.common.Result;
import com.xk.entity.Message;
import com.xk.mapper.EvaluationMapper;
import com.xk.service.EvaluationService;
import com.xk.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.xk.utils.TypeConversionUtils.toInteger;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private EvaluationMapper evaluationMapper;

    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> getPendingEvaluations(@RequestParam Integer userId) {
        return Result.success(evaluationService.getPendingEvaluations(userId));
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistoryEvaluations(@RequestParam Integer userId) {
        return Result.success(evaluationService.getHistoryEvaluations(userId));
    }

    @PostMapping("/submit")
    public Result<?> submitEvaluation(@RequestBody Map<String, Object> request) {
        Integer evaluatorId = (Integer) request.get("evaluatorId");
        Integer evaluatedId = (Integer) request.get("evaluatedId");
        Integer jobId = (Integer) request.get("jobId");
        Integer workAssignmentId = (Integer) request.get("workAssignmentId");
        Integer rating = (Integer) request.get("rating");
        String comment = (String) request.get("comment");
        Boolean isPositive = (Boolean) request.get("isPositive");

        String res = evaluationService.submitEvaluation(evaluatorId, evaluatedId, jobId, workAssignmentId, rating, comment, isPositive);
        return "success".equals(res) ? Result.successMsg("评价成功") : Result.error(res);
    }

    @GetMapping("/job/{jobId}")
    public Result<List<Map<String, Object>>> getEvaluationsByJobId(@PathVariable Integer jobId) {
        return Result.success(evaluationService.getEvaluationsByJobId(jobId));
    }

    @PutMapping("/{id}")
    public Result<?> updateEvaluation(@PathVariable Integer id, @RequestBody Map<String, Object> request) {
        Integer rating = (Integer) request.get("rating");
        String comment = (String) request.get("comment");
        Integer userId = (Integer) request.get("userId");
        String res = evaluationService.updateEvaluation(id, rating, comment, userId);
        return "success".equals(res) ? Result.successMsg("修改成功") : Result.error(res);
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteEvaluation(@PathVariable Integer id, @RequestParam Integer userId) {
        String res = evaluationService.deleteEvaluation(id, userId);
        return "success".equals(res) ? Result.successMsg("删除成功") : Result.error(res);
    }

    @PostMapping("/{id}/reply")
    public Result<?> replyEvaluation(@PathVariable Integer id, @RequestBody Map<String, Object> request) {
        Integer merchantId = (Integer) request.get("merchantId");
        String reply = (String) request.get("reply");
        String res = evaluationService.replyEvaluation(id, merchantId, reply);
        if ("success".equals(res)) {
            Map<String, Object> eval = evaluationService.getEvaluationById(id);
            if (eval != null) {
                Integer evaluatorId = toInteger(eval.get("evaluator_id"));
                Message message = new Message();
                message.setSenderId(merchantId);
                message.setReceiverId(evaluatorId);
                message.setType("evaluation_reply");
                message.setContent("您的评价收到了商户回复：" + reply);
                message.setRelatedId(id);
                messageService.sendMessage(message);
            }
            return Result.successMsg("回复成功");
        }
        return Result.error(res);
    }

    @GetMapping("/pending-merchant")
    public Result<List<Map<String, Object>>> getPendingMerchantEvaluations(@RequestParam Integer merchantId) {
        return Result.success(evaluationMapper.getPendingMerchantEvaluations(merchantId));
    }

    @PostMapping("/submit-merchant")
    public Result<?> submitMerchantEvaluation(@RequestBody Map<String, Object> request) {
        Integer evaluatorId = (Integer) request.get("evaluatorId");
        Integer evaluatedId = (Integer) request.get("evaluatedId");
        Integer jobId = (Integer) request.get("jobId");
        Integer workAssignmentId = (Integer) request.get("workAssignmentId");
        Integer rating = (Integer) request.get("rating");
        String comment = (String) request.get("comment");

        if (evaluatorId == null || evaluatedId == null || jobId == null || workAssignmentId == null || rating == null) {
            return Result.error("参数缺失");
        }

        Boolean isPositive = rating >= 3;
        String res = evaluationService.submitMerchantEvaluation(evaluatorId, evaluatedId, jobId, workAssignmentId, rating, comment, isPositive);
        return "success".equals(res) ? Result.successMsg("评价成功") : Result.error(res);
    }

    @GetMapping("/student-reviews/{studentId}")
    public Result<List<Map<String, Object>>> getStudentReviews(@PathVariable Integer studentId) {
        return Result.success(evaluationMapper.getMerchantEvaluationsForStudent(studentId));
    }
}
