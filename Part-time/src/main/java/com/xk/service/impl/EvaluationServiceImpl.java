package com.xk.service.impl;

import com.xk.entity.Evaluation;
import com.xk.mapper.AssignmentMapper;
import com.xk.mapper.AttendanceMapper;
import com.xk.mapper.EvaluationMapper;
import com.xk.service.CreditService;
import com.xk.service.EvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static com.xk.utils.TypeConversionUtils.toInteger;

@Service
public class EvaluationServiceImpl implements EvaluationService {

    @Autowired
    private EvaluationMapper evaluationMapper;
    @Autowired
    private CreditService creditService;
    @Autowired
    private AssignmentMapper assignmentMapper;
    @Autowired
    private AttendanceMapper attendanceMapper;

    @Override
    public List<Map<String, Object>> getPendingEvaluations(Integer userId) {
        return evaluationMapper.getPendingEvaluations(userId);
    }

    @Override
    public List<Map<String, Object>> getHistoryEvaluations(Integer userId) {
        return evaluationMapper.getHistoryEvaluations(userId);
    }

    @Override
    public String submitEvaluation(Integer evaluatorId, Integer evaluatedId, Integer jobId,
                                   Integer workAssignmentId, Integer rating, String comment, Boolean isPositive) {
        // 1. 检查工作分配是否完成
        Map<String, Object> workAssignment = assignmentMapper.getAssignmentById(workAssignmentId);
        if (workAssignment == null || !"completed".equals(workAssignment.get("status")))
            return "工作尚未完成";

        // 2. 从考勤表获取签到签退时间
        Map<String, Object> attendance = attendanceMapper.getAttendanceByAssignmentId(workAssignmentId);
        if (attendance == null)
            return "未找到考勤记录";
        Object checkInObj = attendance.get("check_in_time");
        Object checkOutObj = attendance.get("check_out_time");
        if (checkInObj == null || checkOutObj == null)
            return "考勤记录不完整";

        LocalDateTime checkInTime = ((java.sql.Timestamp) checkInObj).toLocalDateTime();
        LocalDateTime checkOutTime = ((java.sql.Timestamp) checkOutObj).toLocalDateTime();
        long hours = ChronoUnit.HOURS.between(checkInTime, checkOutTime);
        if (hours < 4)
            return "工作时长不足4小时";

        // 3. 是否已评价
        Map<String, Object> existing = evaluationMapper.getEvaluationByAssignmentId(workAssignmentId);
        if (existing != null) return "已经评价过了";

        // 4. 插入评价
        Evaluation eval = new Evaluation();
        eval.setEvaluatorId(evaluatorId);
        eval.setEvaluatedId(evaluatedId);
        eval.setJobId(jobId);
        eval.setWorkAssignmentId(workAssignmentId);
        eval.setRating(rating);
        eval.setComment(comment);
        eval.setIsPositive(isPositive);
        eval.setType("student_to_merchant");      // 添加这一行
        eval.setCreatedAt(LocalDateTime.now());
        evaluationMapper.insertEvaluation(eval);

        // 5. 信用分影响
        if (isPositive) {
            creditService.addCredit(evaluatedId, 3, "获得好评", "praise");
        } else {
            creditService.addCredit(evaluatedId, -5, "获得差评", "criticize");
        }
        return "success";
    }
    @Override
    public List<Map<String, Object>> getEvaluationsByJobId(Integer jobId) {
        return evaluationMapper.getEvaluationsByJobId(jobId);
    }

    @Override
    public String updateEvaluation(Integer evaluationId, Integer rating, String comment, Integer userId) {
        Map<String, Object> oldEval = evaluationMapper.getEvaluationById(evaluationId);
        if (oldEval == null) return "评价不存在";
        Integer evaluatorId = toInteger(oldEval.get("evaluator_id"));
        if (!evaluatorId.equals(userId)) return "只能修改自己的评价";

        // 检查是否超过 24 小时
        LocalDateTime createdAt = null;
        Object createdAtObj = oldEval.get("created_at");
        if (createdAtObj instanceof java.sql.Timestamp) {
            createdAt = ((java.sql.Timestamp) createdAtObj).toLocalDateTime();
        } else if (createdAtObj instanceof LocalDateTime) {
            createdAt = (LocalDateTime) createdAtObj;
        }
        if (createdAt != null && ChronoUnit.HOURS.between(createdAt, LocalDateTime.now()) > 24) {
            return "评价已超过24小时，无法修改";
        }

        int oldRating = toInteger(oldEval.get("rating"));
        Boolean oldIsPositive = (Boolean) oldEval.get("is_positive");
        Integer evaluatedId = toInteger(oldEval.get("evaluated_id"));

        // 1. 先更新评价内容，使用旧评分作为条件，防止并发重复提交
        int rows = evaluationMapper.updateEvaluationWithOldRating(evaluationId, rating, comment, oldRating);
        if (rows == 0) {
            return "评价已被修改，请刷新后重试";
        }

        // 2. 撤销旧信用分
        if (oldIsPositive != null && oldIsPositive) {
            creditService.addCredit(evaluatedId, -5, "评价被修改，撤销好评", "evaluation_revoke");
        } else {
            creditService.addCredit(evaluatedId, 5, "评价被修改，撤销差评", "evaluation_revoke");
        }

        // 3. 更新好评/差评标记
        boolean newIsPositive = rating >= 3;
        evaluationMapper.updateEvaluationIsPositive(evaluationId, newIsPositive);

        // 4. 应用新信用分
        if (newIsPositive) {
            creditService.addCredit(evaluatedId, 5, "评价被修改为好评", "praise");
        } else {
            creditService.addCredit(evaluatedId, -5, "评价被修改为差评", "criticize");
        }

        return "success";
    }

    @Override
    public String deleteEvaluation(Integer evaluationId, Integer userId) {
        Map<String, Object> eval = evaluationMapper.getEvaluationById(evaluationId); // 改为通过评价ID查询
        if (eval == null) return "评价不存在";
        Integer evaluatorId = toInteger(eval.get("evaluator_id"));
        if (!evaluatorId.equals(userId)) return "只能删除自己的评价";
        evaluationMapper.deleteEvaluation(evaluationId);
        return "success";
    }

    @Override
    public String replyEvaluation(Integer evaluationId, Integer merchantId, String reply) {
        Map<String, Object> eval = evaluationMapper.getEvaluationById(evaluationId); // 修改这里
        if (eval == null) return "评价不存在";
        Integer evaluatedId = toInteger(eval.get("evaluated_id"));
        if (!evaluatedId.equals(merchantId)) return "只能回复自己的评价";
        evaluationMapper.replyEvaluation(evaluationId, reply, LocalDateTime.now());
        return "success";
    }
    @Override
    public Map<String, Object> getEvaluationById(Integer evaluationId) {
        return evaluationMapper.getEvaluationById(evaluationId);
    }

    @Override
    public String submitMerchantEvaluation(Integer evaluatorId, Integer evaluatedId, Integer jobId,
                                           Integer workAssignmentId, Integer rating, String comment, Boolean isPositive) {
        // 1. 检查是否已经评价过（防止重复提交）
        Map<String, Object> existing = evaluationMapper.getMerchantEvaluationByAssignmentId(workAssignmentId);
        if (existing != null) {
            return "已经评价过了";
        }

        // 2. 构建评价实体
        Evaluation eval = new Evaluation();
        eval.setEvaluatorId(evaluatorId);
        eval.setEvaluatedId(evaluatedId);
        eval.setJobId(jobId);
        eval.setWorkAssignmentId(workAssignmentId);
        eval.setRating(rating);
        eval.setComment(comment);
        eval.setIsPositive(isPositive);
        eval.setType("merchant_to_student");   // 指定评价类型
        eval.setCreatedAt(LocalDateTime.now());

        // 3. 插入数据库
        int rows = evaluationMapper.insertEvaluation(eval);
        if (rows == 0) {
            return "评价保存失败";
        }

        // 4. 信用分联动（好评 +5，差评 -5）
        if (isPositive) {
            creditService.addCredit(evaluatedId, 5, "获得商户好评", "merchant_praise");
        } else {
            creditService.addCredit(evaluatedId, -5, "获得商户差评", "merchant_criticize");
        }

        return "success";
    }
}