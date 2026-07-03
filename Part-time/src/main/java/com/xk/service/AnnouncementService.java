package com.xk.service;

import com.xk.dto.AnnouncementDTO;
import com.xk.entity.Announcement;

import java.util.List;

public interface AnnouncementService {
    int addAnnouncement(Announcement announcement);
    boolean updateAnnouncement(Announcement announcement);
    boolean deleteAnnouncement(Integer id);
    Announcement getAnnouncementById(Integer id);
    List<Announcement> getAllAnnouncements();
    List<Announcement> getAnnouncementsByUserId(Integer userId);
    List<Announcement> getPublishedAnnouncements();
    AnnouncementDTO addAnnouncementDTO(AnnouncementDTO announcementDTO);
    AnnouncementDTO updateAnnouncementDTO(AnnouncementDTO announcementDTO);
}