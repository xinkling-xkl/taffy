package com.xk.mapper;

import com.xk.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FeedbackMapper {
    int insert(Feedback feedback);
    int deleteById(@Param("id") int id);
    int update(Feedback feedback);
    Feedback selectById(@Param("id") int id);
    List<Feedback> selectByUserId(@Param("userId") int userId);
    List<Feedback> selectAll();
}
