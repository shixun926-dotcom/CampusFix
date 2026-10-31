package com.campusfix.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusfix.order.entity.Complaint;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ComplaintMapper extends BaseMapper<Complaint> {
}
