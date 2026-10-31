package com.campusfix.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusfix.order.entity.Evaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /** 维修工评价汇总：均分与各维度均分 */
    @Select("""
            SELECT COUNT(*) AS cnt,
                   COALESCE(AVG(total_score), 0) AS avgTotal,
                   COALESCE(AVG(speed_score), 0) AS avgSpeed,
                   COALESCE(AVG(attitude_score), 0) AS avgAttitude,
                   COALESCE(AVG(effect_score), 0) AS avgEffect
            FROM evaluation
            WHERE repairer_id = #{repairerId}
            """)
    Map<String, Object> summaryOf(@Param("repairerId") Long repairerId);
}
