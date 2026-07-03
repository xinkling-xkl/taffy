package com.xk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface MatchingHistoryMapper {
    int insert(@Param("userId") int userId, @Param("query") String query,
               @Param("result") String result, @Param("matchCount") int matchCount);
    List<Map<String, Object>> selectByUserId(@Param("userId") int userId,
                                             @Param("limit") int limit);
    int deleteById(@Param("id") int id);
    int deleteByUserId(@Param("userId") int userId);
}