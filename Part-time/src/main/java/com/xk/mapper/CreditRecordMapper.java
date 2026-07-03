package com.xk.mapper;

import com.xk.entity.CreditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CreditRecordMapper {
    List<CreditRecord> getCreditRecordsByUserId(@Param("userId") int userId);
    int insertCreditRecord(CreditRecord creditRecord);
    int getViolationCount(@Param("userId") int userId);
}
