package com.campusfix.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusfix.order.entity.RepairOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {

    /** 当天已用的最大日序号，用于单号生成在重启后不重复 */
    @Select("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(order_no, 9) AS UNSIGNED)), 0)
            FROM repair_order
            WHERE order_no LIKE CONCAT(#{day}, '%')
            """)
    int selectMaxSeqOfDay(@Param("day") String day);

    /** 各维修工当前在办工单数（24h 自动派单选人：在办最少的优先） */
    @Select("""
            SELECT assignee_id AS assigneeId, COUNT(*) AS cnt
            FROM repair_order
            WHERE deleted = 0
              AND assignee_id IS NOT NULL
              AND status IN ('ASSIGNED', 'ACCEPTED', 'PROCESSING', 'SUSPENDED')
            GROUP BY assignee_id
            """)
    List<Map<String, Object>> countWipByAssignee();

    /** 超过时限仍停留在待处理且无人接单的工单。LIMIT 写死，避免 `LIMIT ?` 在部分驱动下失效。 */
    @Select("""
            SELECT *
            FROM repair_order
            WHERE deleted = 0
              AND status = 'PENDING'
              AND assignee_id IS NULL
              AND COALESCE(review_at, created_at) <= #{deadline}
            ORDER BY priority DESC, created_at ASC
            LIMIT 50
            """)
    List<RepairOrder> selectAutoAssignCandidates(@Param("deadline") LocalDateTime deadline);
}
