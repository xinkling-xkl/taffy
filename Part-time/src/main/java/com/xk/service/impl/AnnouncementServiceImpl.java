package com.xk.service.impl;

import com.xk.dto.AnnouncementDTO;
import com.xk.entity.Announcement;
import com.xk.mapper.AnnouncementMapper;
import com.xk.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    @Autowired
    private AnnouncementMapper announcementMapper;

    @Override
    public int addAnnouncement(Announcement announcement) {
        announcement.setCreatedAt(new Date());
        announcement.setUpdatedAt(new Date());
        if (announcement.getStatus() == 1 && announcement.getPublishedAt() == null) {
            announcement.setPublishedAt(new Date());
        }
        if (announcement.getIsPinned() == null) {
            announcement.setIsPinned(false);
        }
        if (announcement.getPriority() == null) {
            announcement.setPriority(0);
        }
        if (announcement.getStatus() == null) {
            announcement.setStatus(0);
        }
        return announcementMapper.insert(announcement);
    }

    @Override
    public boolean updateAnnouncement(Announcement announcement) {
        announcement.setUpdatedAt(new Date());
        if (announcement.getStatus() == 1 && announcement.getPublishedAt() == null) {
            announcement.setPublishedAt(new Date());
        }
        return announcementMapper.update(announcement) > 0;
    }

    @Override
    public boolean deleteAnnouncement(Integer id) {
        return announcementMapper.deleteById(id) > 0;
    }

    @Override
    public Announcement getAnnouncementById(Integer id) {
        return announcementMapper.selectById(id);
    }

    @Override
    public List<Announcement> getAllAnnouncements() {
        return announcementMapper.selectAll();
    }

    @Override
    public List<Announcement> getAnnouncementsByUserId(Integer userId) {
        return announcementMapper.selectByUserId(userId);
    }

    @Override
    public List<Announcement> getPublishedAnnouncements() {
        return announcementMapper.selectPublished();
    }

    @Override
    public AnnouncementDTO addAnnouncementDTO(AnnouncementDTO announcementDTO) {
        Announcement announcement = toEntity(announcementDTO);
        int result = addAnnouncement(announcement);
        if (result > 0) {
            return toDTO(announcement);
        }
        return null;
    }

    @Override
    public AnnouncementDTO updateAnnouncementDTO(AnnouncementDTO announcementDTO) {
        Announcement announcement = toEntity(announcementDTO);
        boolean success = updateAnnouncement(announcement);
        if (success) {
            return toDTO(announcement);
        }
        return null;
    }

    private AnnouncementDTO toDTO(Announcement announcement) {
        if (announcement == null) return null;
        AnnouncementDTO dto = new AnnouncementDTO();
        dto.setId(announcement.getId());
        dto.setTitle(announcement.getTitle());
        dto.setContent(announcement.getContent());
        dto.setUserId(announcement.getUserId());
        dto.setIsPinned(announcement.getIsPinned());
        dto.setPriority(announcement.getPriority());
        dto.setStatus(announcement.getStatus());
        dto.setPublishedAt(announcement.getPublishedAt());
        dto.setCreatedAt(announcement.getCreatedAt());
        dto.setUpdatedAt(announcement.getUpdatedAt());
        return dto;
    }

    private Announcement toEntity(AnnouncementDTO dto) {
        if (dto == null) return null;
        Announcement announcement = new Announcement();
        announcement.setId(dto.getId());
        announcement.setTitle(dto.getTitle());
        announcement.setContent(dto.getContent());
        announcement.setUserId(dto.getUserId());
        announcement.setIsPinned(dto.getIsPinned());
        announcement.setPriority(dto.getPriority());
        announcement.setStatus(dto.getStatus());
        announcement.setPublishedAt(dto.getPublishedAt());
        announcement.setCreatedAt(dto.getCreatedAt());
        announcement.setUpdatedAt(dto.getUpdatedAt());
        return announcement;
    }
}