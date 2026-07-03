package com.xk.mapper;

import com.xk.entity.Identity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IdentityMapper {
    int insert(Identity identity);
    int deleteById(@Param("id") int id);
    int update(Identity identity);
    Identity selectById(@Param("id") int id);
    List<Identity> selectByUserId(@Param("userId") int userId);
    List<Identity> selectAll();
}
