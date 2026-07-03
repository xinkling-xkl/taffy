package com.xk.mapper;

import com.xk.entity.Announcement;

import java.util.List;

public interface AnnouncementMapper {
    int insert(Announcement announcement);
    int update(Announcement announcement);
    int deleteById(Integer id);
    Announcement selectById(Integer id);
    List<Announcement> selectAll();
    List<Announcement> selectByUserId(Integer userId);
    List<Announcement> selectPublished();
}