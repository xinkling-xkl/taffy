package com.xk.controller;

import com.xk.common.Result;
import com.xk.dto.AnnouncementDTO;
import com.xk.entity.Announcement;
import com.xk.entity.User;
import com.xk.exception.BusinessException;
import com.xk.service.AnnouncementService;
import com.xk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcement")
public class AnnouncementController {

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private UserService userService;

    @GetMapping("/list")
    public Result<List<Announcement>> getAllAnnouncements() {
        return Result.success(announcementService.getAllAnnouncements());
    }

    @GetMapping("/published")
    public Result<List<Announcement>> getPublishedAnnouncements() {
        return Result.success(announcementService.getPublishedAnnouncements());
    }

    @GetMapping("/my")
    public Result<List<Announcement>> getMyAnnouncements(@RequestParam("userId") int userId) {
        return Result.success(announcementService.getAnnouncementsByUserId(userId));
    }

    @GetMapping("/{id}")
    public Result<Announcement> getAnnouncementById(@PathVariable("id") int id) {
        Announcement announcement = announcementService.getAnnouncementById(id);
        return announcement != null ? Result.success(announcement) : Result.error("公告不存在");
    }

    @PostMapping("/add")
    public Result<AnnouncementDTO> addAnnouncement(@RequestBody AnnouncementDTO announcementDTO, @RequestParam("userId") int userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"管理员".equals(user.getIdentity())) {
            throw new BusinessException(403, "只有管理员可以发布公告");
        }
        announcementDTO.setUserId(userId);
        AnnouncementDTO created = announcementService.addAnnouncementDTO(announcementDTO);
        if (created == null) {
            throw new BusinessException("公告发布失败");
        }
        return Result.success("公告发布成功", created);
    }

    @PutMapping("/update")
    public Result<AnnouncementDTO> updateAnnouncement(@RequestBody AnnouncementDTO announcementDTO, @RequestParam("userId") int userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"管理员".equals(user.getIdentity())) {
            throw new BusinessException(403, "只有管理员可以更新公告");
        }
        Announcement original = announcementService.getAnnouncementById(announcementDTO.getId());
        if (original == null) {
            throw new BusinessException("公告不存在");
        }
        announcementDTO.setUserId(original.getUserId());
        announcementDTO.setIsPinned(original.getIsPinned());
        announcementDTO.setPriority(original.getPriority());
        announcementDTO.setPublishedAt(original.getPublishedAt());
        announcementDTO.setCreatedAt(original.getCreatedAt());
        AnnouncementDTO updated = announcementService.updateAnnouncementDTO(announcementDTO);
        if (updated == null) {
            throw new BusinessException("公告更新失败");
        }
        return Result.success("公告更新成功", updated);
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteAnnouncement(@PathVariable("id") int id, @RequestParam("userId") int userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"管理员".equals(user.getIdentity())) {
            throw new BusinessException(403, "只有管理员可以删除公告");
        }
        Announcement announcement = announcementService.getAnnouncementById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        boolean success = announcementService.deleteAnnouncement(id);
        return success ? Result.successMsg("公告删除成功") : Result.error("公告删除失败");
    }
}
