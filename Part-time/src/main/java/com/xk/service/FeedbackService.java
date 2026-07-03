package com.xk.service;

import com.xk.dto.FeedbackDTO;
import com.xk.entity.Feedback;

import java.util.List;

public interface FeedbackService {
    int submitFeedback(Feedback feedback);
    boolean withdrawFeedback(int id, int userId);
    boolean deleteFeedback(int id, int userId, boolean isAdmin);
    boolean resolveFeedback(int id, String reply);
    List<Feedback> getFeedbackByUserId(int userId);
    List<Feedback> getAllFeedback();
    Feedback getFeedbackById(int id);
    int submitFeedbackDTO(FeedbackDTO feedbackDTO);
    FeedbackDTO getFeedbackDTOById(int id);
    List<FeedbackDTO> getFeedbackDTOByUserId(int userId);
    List<FeedbackDTO> getAllFeedbackDTO();
}
