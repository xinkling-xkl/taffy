package com.xk.controller;

import com.xk.common.Result;
import com.xk.dto.FeedbackDTO;
import com.xk.exception.BusinessException;
import com.xk.service.FeedbackService;
import com.xk.util.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @Value("${file.upload.path}")
    private String uploadPath;

    @PostMapping("/upload-image")
    public Result<String> uploadFeedbackImage(@RequestParam("file") MultipartFile file) {
        try {
            String imagePath = FileUtils.uploadImage(file, uploadPath, "feedback");
            return Result.success("图片上传成功", imagePath);
        } catch (Exception e) {
            throw new BusinessException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @PostMapping("/submit")
    public Result<?> submitFeedback(@RequestBody FeedbackDTO feedbackDTO) {
        int res = feedbackService.submitFeedbackDTO(feedbackDTO);
        return res > 0 ? Result.successMsg("反馈提交成功") : Result.error("反馈提交失败");
    }

    @DeleteMapping("/withdraw/{id}")
    public Result<?> withdrawFeedback(@PathVariable("id") int id, @RequestParam("userId") int userId) {
        boolean success = feedbackService.withdrawFeedback(id, userId);
        return success ? Result.successMsg("反馈撤回成功") : Result.error("反馈撤回失败");
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> deleteFeedback(@PathVariable("id") int id, @RequestParam("userId") int userId, @RequestParam("isAdmin") boolean isAdmin) {
        boolean success = feedbackService.deleteFeedback(id, userId, isAdmin);
        return success ? Result.successMsg("反馈删除成功") : Result.error("反馈删除失败");
    }

    @PutMapping("/resolve/{id}")
    public Result<?> resolveFeedback(@PathVariable("id") int id, @RequestParam("reply") String reply) {
        boolean success = feedbackService.resolveFeedback(id, reply);
        return success ? Result.successMsg("反馈解决成功") : Result.error("反馈解决失败");
    }

    @GetMapping("/user/{userId}")
    public Result<List<FeedbackDTO>> getUserFeedback(@PathVariable("userId") int userId) {
        return Result.success(feedbackService.getFeedbackDTOByUserId(userId));
    }

    @GetMapping("/all")
    public Result<List<FeedbackDTO>> getAllFeedback() {
        return Result.success(feedbackService.getAllFeedbackDTO());
    }

    @GetMapping("/detail/{id}")
    public Result<FeedbackDTO> getFeedbackDetail(@PathVariable("id") int id) {
        FeedbackDTO dto = feedbackService.getFeedbackDTOById(id);
        return dto != null ? Result.success(dto) : Result.error("反馈不存在");
    }
}
