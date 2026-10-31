package com.campusfix.stats.dto;

import java.util.List;
import java.util.Map;

/**
 * 统计传输对象。口径见基线 §12：完成类统计一律按 finished_at，
 * 取消与驳回不计入任何完成类统计。
 */
public final class StatsDtos {

    private StatsDtos() {
    }

    /** 今日 / 本周（自然周，周一起）/ 本月完成数 */
    public record Completions(long today, long week, long month) {
    }

    public record TrendPoint(String date, long count) {
    }

    public record NameCount(String name, long count) {
    }

    /**
     * 工作台总览：一次请求返回全部图表数据，替代前端「100 条样本估算」。
     * 数据范围按角色：管理员全局、后勤本部门（含下级）、维修工/学生个人。
     */
    public record Overview(
            long total,
            /** 10 种状态的实时计数（key 为状态枚举名） */
            Map<String, Long> statusCounts,
            Completions completed,
            /** 平均处理时长（接单→完成，分钟）；无样本时为 0 */
            long avgHandleMinutes,
            /** 平均维修时长（开始→完成，分钟）；无样本时为 0 */
            long avgRepairMinutes,
            /** 近 7 天新增工单趋势（含今日） */
            List<TrendPoint> trend,
            /** 故障类型 TOP5（全部工单按报修量） */
            List<NameCount> categoryTop,
            /** 楼栋分布 TOP5（已完成工单，基线口径） */
            List<NameCount> buildingTop) {
    }

    /** 维修工绩效排行：按加权平均分排序，无评价的排后。 */
    public record RepairerRank(
            Long repairerId,
            String realName,
            String deptName,
            long completedCount,
            /** 三维加权平均分（1-5），无评价为 null */
            Double avgScore,
            /** 好评率（总分 ≥4 占比 %），无评价为 null */
            Double goodRatePercent,
            long avgHandleMinutes) {
    }

    /** 月度趋势点：新增工单数 + 完成数。 */
    public record MonthPoint(String month, long total, long completed) {
    }

    /** 投诉汇总（投诉率口径沿用基线：投诉数 / (工单总数 + 10) × 100%）。 */
    public record ComplaintSummary(long total, long pending, long processing,
                                   long resolved, long rejected, double ratePercent) {
    }

    /** 数据统计页总览（管理端）：绩效排行 + 月度趋势 + 投诉汇总。 */
    public record Insight(List<RepairerRank> repairerRank,
                          List<MonthPoint> monthlyTrend,
                          ComplaintSummary complaint) {
    }
}
