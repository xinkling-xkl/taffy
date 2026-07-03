package com.xk.service.impl;

import com.xk.dto.FeedbackDTO;
import com.xk.entity.Feedback;
import com.xk.mapper.FeedbackMapper;
import com.xk.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    @Autowired
    private FeedbackMapper feedbackMapper;

    @Override
    public int submitFeedback(Feedback feedback) {
        feedback.setData(new Date());
        feedback.setStatus("未解决");
        return feedbackMapper.insert(feedback);
    }

    @Override
    public boolean withdrawFeedback(int id, int userId) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) return false;
        if (feedback.getUserId() != userId) return false;
        if (!"未解决".equals(feedback.getStatus())) return false;
        return feedbackMapper.deleteById(id) > 0;
    }

    @Override
    public boolean deleteFeedback(int id, int userId, boolean isAdmin) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) return false;
        if (feedback.getUserId() != userId && !isAdmin) return false;
        return feedbackMapper.deleteById(id) > 0;
    }

    @Override
    public boolean resolveFeedback(int id, String reply) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) return false;
        feedback.setStatus("已解决");
        feedback.setReply(reply);
        feedback.setData(new Date());
        return feedbackMapper.update(feedback) > 0;
    }

    @Override
    public List<Feedback> getFeedbackByUserId(int userId) {
        return feedbackMapper.selectByUserId(userId);
    }

    @Override
    public List<Feedback> getAllFeedback() {
        return feedbackMapper.selectAll();
    }

    @Override
    public Feedback getFeedbackById(int id) {
        return feedbackMapper.selectById(id);
    }

    @Override
    public int submitFeedbackDTO(FeedbackDTO feedbackDTO) {
        Feedback feedback = toFeedbackEntity(feedbackDTO);
        return submitFeedback(feedback);
    }

    @Override
    public FeedbackDTO getFeedbackDTOById(int id) {
        Feedback feedback = feedbackMapper.selectById(id);
        return toFeedbackDTO(feedback);
    }

    @Override
    public List<FeedbackDTO> getFeedbackDTOByUserId(int userId) {
        List<Feedback> feedbacks = feedbackMapper.selectByUserId(userId);
        return feedbacks.stream()
                .map(this::toFeedbackDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FeedbackDTO> getAllFeedbackDTO() {
        List<Feedback> feedbacks = feedbackMapper.selectAll();
        return feedbacks.stream()
                .map(this::toFeedbackDTO)
                .collect(Collectors.toList());
    }

    private FeedbackDTO toFeedbackDTO(Feedback feedback) {
        if (feedback == null) return null;
        FeedbackDTO dto = new FeedbackDTO();
        dto.setId(feedback.getId());
        dto.setUserId(feedback.getUserId());
        dto.setContent(feedback.getContent());
        dto.setReply(feedback.getReply());
        dto.setImage(feedback.getImage());
        dto.setStatus(feedback.getStatus());
        dto.setData(feedback.getData());
        return dto;
    }

    private Feedback toFeedbackEntity(FeedbackDTO dto) {
        if (dto == null) return null;
        Feedback feedback = new Feedback();
        if (dto.getId() != null) feedback.setId(dto.getId());
        feedback.setUserId(dto.getUserId() != null ? dto.getUserId() : 0);
        feedback.setContent(dto.getContent());
        feedback.setReply(dto.getReply());
        feedback.setImage(dto.getImage());
        feedback.setStatus(dto.getStatus());
        feedback.setData(dto.getData());
        return feedback;
    }
}
