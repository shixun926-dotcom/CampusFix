package com.campusfix.stats.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusfix.asset.service.LocationService;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.dto.OrderDtos.CategoryVO;
import com.campusfix.order.entity.Complaint;
import com.campusfix.order.entity.Evaluation;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.mapper.ComplaintMapper;
import com.campusfix.order.mapper.EvaluationMapper;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.order.service.CategoryService;
import com.campusfix.stats.dto.StatsDtos;
import com.campusfix.stats.dto.StatsDtos.Completions;
import com.campusfix.stats.dto.StatsDtos.Insight;
import com.campusfix.stats.dto.StatsDtos.MonthPoint;
import com.campusfix.stats.dto.StatsDtos.NameCount;
import com.campusfix.stats.dto.StatsDtos.Overview;
import com.campusfix.stats.dto.StatsDtos.RepairerRank;
import com.campusfix.stats.dto.StatsDtos.TrendPoint;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysDeptMapper;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.service.DeptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据统计（基线 §12 口径）。
 *
 * <p>数据范围与工单列表一致：ADMIN 全部、MANAGER 本部门及下级、
 * REPAIRER 名下 + 自报、STUDENT 仅自报；在 SQL 层拼 scope，不信任前端传参。</p>
 *
 * <p>口径要点：</p>
 * <ul>
 *   <li>完成类统计（今日/本周/本月）按 {@code finished_at}，状态必须 COMPLETED；</li>
 *   <li>取消、驳回不计入任何完成类统计；</li>
 *   <li>平均处理时长 = AVG(finished_at - accepted_at)，平均维修时长 = AVG(finished_at - started_at)；</li>
 *   <li>故障类型分布按全部工单（报修量视角，供工作台排行）；楼栋分布按已完成工单（基线口径）。</li>
 * </ul>
 *
 * <p>年约 7,300 单的规模下所有聚合都是毫秒级，不建预聚合表（基线 §12.4）；
 * 本项目不引入 XML 统计 SQL，聚合用 QueryWrapper 的原生 select/groupBy 完成，
 * 条件只来自登录用户身份，无外部输入拼接。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    private final RepairOrderMapper orderMapper;
    private final EvaluationMapper evaluationMapper;
    private final ComplaintMapper complaintMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final DeptService deptService;
    private final CategoryService categoryService;
    private final LocationService locationService;

    public Overview overview(LoginUser me) {
        QueryWrapper<RepairOrder> scope = scopeOf(me);

        long total = orderMapper.selectCount(cloneScope(me));

        // 各状态实时计数
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        List<Map<String, Object>> statusRows = orderMapper.selectMaps(
                cloneScope(me).select("status", "COUNT(*) AS cnt").groupBy("status"));
        for (Map<String, Object> row : statusRows) {
            statusCounts.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }

        // 今日 / 本周 / 本月完成数（finished_at 口径）
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        Completions completed = new Completions(
                completedCount(me, todayStart),
                completedCount(me, weekStart),
                completedCount(me, monthStart));

        // 平均处理 / 维修时长（一条 SQL 取两个聚合）
        long avgHandle = 0;
        long avgRepair = 0;
        List<Map<String, Object>> avgRows = orderMapper.selectMaps(cloneScope(me)
                .select("COALESCE(ROUND(AVG(TIMESTAMPDIFF(MINUTE, accepted_at, finished_at))), 0) AS handle",
                        "COALESCE(ROUND(AVG(TIMESTAMPDIFF(MINUTE, started_at, finished_at))), 0) AS repair")
                .eq("status", OrderStatus.COMPLETED.name()));
        if (!avgRows.isEmpty()) {
            avgHandle = ((Number) avgRows.get(0).getOrDefault("handle", 0)).longValue();
            avgRepair = ((Number) avgRows.get(0).getOrDefault("repair", 0)).longValue();
        }

        return new Overview(total, statusCounts, completed, avgHandle, avgRepair,
                trend(me, today), categoryTop(me), buildingTop(me));
    }

    // ------------------------------------------------------------------ 内部

    private long completedCount(LoginUser me, LocalDateTime from) {
        return orderMapper.selectCount(cloneScope(me)
                .eq("status", OrderStatus.COMPLETED.name())
                .ge("finished_at", from));
    }

    /** 近 7 天新增趋势（created_at 口径，与前端原「报修趋势」语义一致） */
    private List<TrendPoint> trend(LoginUser me, LocalDate today) {
        LocalDate start = today.minusDays(6);
        Map<String, Long> byDay = new HashMap<>();
        List<Map<String, Object>> rows = orderMapper.selectMaps(cloneScope(me)
                .select("DATE(created_at) AS d", "COUNT(*) AS cnt")
                .ge("created_at", start.atStartOfDay())
                .groupBy("DATE(created_at)"));
        for (Map<String, Object> row : rows) {
            byDay.put(String.valueOf(row.get("d")), ((Number) row.get("cnt")).longValue());
        }
        List<TrendPoint> list = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            list.add(new TrendPoint(d.format(MD), byDay.getOrDefault(d.toString(), 0L)));
        }
        return list;
    }

    /** 故障类型 TOP5：全部工单按报修量（disabled 分类的单归入「已停用分类」） */
    private List<NameCount> categoryTop(LoginUser me) {
        List<Map<String, Object>> rows = orderMapper.selectMaps(cloneScope(me)
                .select("category_id AS cid", "COUNT(*) AS cnt")
                .isNotNull("category_id")
                .groupBy("category_id")
                .orderByDesc("cnt")
                .last("LIMIT 5"));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (CategoryVO c : categoryService.listEnabled()) {
            names.put(c.id(), c.name());
        }
        return rows.stream()
                .map(row -> {
                    Long cid = ((Number) row.get("cid")).longValue();
                    return new NameCount(names.getOrDefault(cid, "已停用分类"),
                            ((Number) row.get("cnt")).longValue());
                })
                .toList();
    }

    /** 楼栋分布 TOP5：已完成工单按 building_id 聚合（基线 §12.2） */
    private List<NameCount> buildingTop(LoginUser me) {
        List<Map<String, Object>> rows = orderMapper.selectMaps(cloneScope(me)
                .select("building_id AS bid", "COUNT(*) AS cnt")
                .eq("status", OrderStatus.COMPLETED.name())
                .isNotNull("building_id")
                .groupBy("building_id")
                .orderByDesc("cnt")
                .last("LIMIT 5"));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(r -> ((Number) r.get("bid")).longValue()).toList();
        Map<Long, String> allNames = locationService.nameMap();
        Map<Long, String> names = new HashMap<>();
        ids.forEach(id -> names.put(id, allNames.get(id)));
        return rows.stream()
                .map(row -> {
                    Long bid = ((Number) row.get("bid")).longValue();
                    return new NameCount(names.getOrDefault(bid, "未知楼栋"),
                            ((Number) row.get("cnt")).longValue());
                })
                .toList();
    }

    /**
     * 数据范围（与 OrderQueryService.applyDataScope 同一套规则，列名为字符串版）：
     * ADMIN 全部；MANAGER 本部门及下级（无部门则退化为仅自报）；
     * REPAIRER 名下 + 自报；其余（STUDENT）仅自报。
     */
    private QueryWrapper<RepairOrder> scopeOf(LoginUser me) {
        QueryWrapper<RepairOrder> w = new QueryWrapper<>();
        if (me.hasRole("ADMIN")) {
            return w;
        }
        if (me.hasRole("MANAGER")) {
            Set<Long> depts = me.getDeptId() == null ? Set.of() : deptService.getDescendantIds(me.getDeptId());
            if (depts.isEmpty()) {
                w.eq("reporter_id", me.getId());
            } else {
                w.in("dept_id", depts);
            }
            return w;
        }
        if (me.hasRole("REPAIRER")) {
            w.and(q -> q.eq("assignee_id", me.getId()).or().eq("reporter_id", me.getId()));
            return w;
        }
        w.eq("reporter_id", me.getId());
        return w;
    }

    /** QueryWrapper 不可复用，每次查询重建 scope */
    private QueryWrapper<RepairOrder> cloneScope(LoginUser me) {
        return scopeOf(me);
    }

    // ------------------------------------------------------------------ 数据统计页（管理端洞察）

    /**
     * 洞察总览：维修工绩效排行 + 近 6 个月趋势 + 投诉汇总。
     * 仅管理端调用（Controller 限 order:review / order:assign）；
     * 后勤管理员的数据范围 = 本部门（含下级）维修工与其部门工单。
     */
    public Insight insight(LoginUser me) {
        boolean admin = me.hasRole(RoleCode.ADMIN);
        Set<Long> deptScope = admin ? Set.of()
                : (me.getDeptId() == null ? Set.of() : deptService.getDescendantIds(me.getDeptId()));

        List<SysUser> repairers = userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, 1)
                        // 与 DispatchService.activeRepairers 同口径：按角色关联表判定维修工，
                        // 不能用 staff_no 判定（测试/现实数据里管理员也可能填工号）
                        .inSql(SysUser::getId,
                                "SELECT user_id FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                                        + "WHERE r.code = '" + RoleCode.REPAIRER + "'"));
        List<SysUser> pool = repairers.stream()
                .filter(u -> admin || (u.getDeptId() != null && deptScope.contains(u.getDeptId())))
                .toList();

        // 完成数 + 平均处理时长（按维修人聚合，仅已完成单）
        Map<Long, long[]> completed = new HashMap<>(); // [count, totalHandleMinutes]
        List<Map<String, Object>> doneRows = orderMapper.selectMaps(new QueryWrapper<RepairOrder>()
                .select("assignee_id AS aid", "COUNT(*) AS cnt",
                        "COALESCE(ROUND(AVG(TIMESTAMPDIFF(MINUTE, accepted_at, finished_at))), 0) AS mins")
                .eq("status", OrderStatus.COMPLETED.name())
                .isNotNull("assignee_id")
                .groupBy("assignee_id"));
        for (Map<String, Object> row : doneRows) {
            long aid = ((Number) row.get("aid")).longValue();
            completed.put(aid, new long[]{((Number) row.get("cnt")).longValue(),
                    ((Number) row.get("mins")).longValue()});
        }

        // 评分：平均分 + 好评率（总分 ≥4）
        Map<Long, double[]> scores = new HashMap<>(); // [avg, goodRate]
        List<Map<String, Object>> scoreRows = evaluationMapper.selectMaps(new QueryWrapper<Evaluation>()
                .select("repairer_id AS rid", "ROUND(AVG(total_score), 2) AS avg_score",
                        "ROUND(SUM(total_score >= 4) * 100.0 / COUNT(*), 1) AS good_rate")
                .groupBy("repairer_id"));
        for (Map<String, Object> row : scoreRows) {
            double[] pair = new double[]{((Number) row.get("avg_score")).doubleValue(),
                    row.get("good_rate") == null ? 0 : ((Number) row.get("good_rate")).doubleValue()};
            scores.put(((Number) row.get("rid")).longValue(), pair);
        }

        Map<Long, String> deptNames = new HashMap<>();
        deptMapper.selectList(null).forEach(d -> deptNames.put(d.getId(), d.getName()));

        List<RepairerRank> rank = pool.stream()
                .map(u -> {
                    long[] c = completed.getOrDefault(u.getId(), new long[]{0, 0});
                    double[] s = scores.get(u.getId());
                    return new RepairerRank(u.getId(), u.getRealName(),
                            deptNames.get(u.getDeptId()), c[0],
                            s == null ? null : s[0], s == null ? null : s[1], c[1]);
                })
                .sorted(java.util.Comparator
                        .comparingDouble((RepairerRank r) -> r.avgScore() == null ? -1 : r.avgScore()).reversed()
                        .thenComparing(r -> r.completedCount())
                        .reversed())
                .toList();

        return new Insight(rank, monthlyTrend(deptScope, admin), complaintSummary(deptScope, admin));
    }

    /** 近 6 个月趋势：按月新增 + 完成（后勤按工单部门过滤）。 */
    private List<MonthPoint> monthlyTrend(Set<Long> deptScope, boolean admin) {
        LocalDate first = LocalDate.now().withDayOfMonth(1).minusMonths(5);

        Map<String, long[]> byMonth = new LinkedHashMap<>();
        QueryWrapper<RepairOrder> w = new QueryWrapper<RepairOrder>()
                .select("DATE_FORMAT(created_at, '%Y-%m') AS m", "COUNT(*) AS cnt",
                        "SUM(status = 'COMPLETED') AS done")
                .ge("created_at", first.atStartOfDay());
        if (!admin) {
            w.apply(deptScope.isEmpty() ? "1=0" : "dept_id IN (" + joinIds(deptScope) + ")");
        }
        w.groupBy("DATE_FORMAT(created_at, '%Y-%m')");
        for (Map<String, Object> row : orderMapper.selectMaps(w)) {
            long[] pair = new long[]{((Number) row.get("cnt")).longValue(),
                    row.get("done") == null ? 0 : ((Number) row.get("done")).longValue()};
            byMonth.put(String.valueOf(row.get("m")), pair);
        }

        List<MonthPoint> list = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String key = first.plusMonths(i).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            long[] pair = byMonth.getOrDefault(key, new long[]{0, 0});
            list.add(new MonthPoint(key.substring(5), pair[0], pair[1]));
        }
        return list;
    }

    /** 投诉汇总 + 投诉率（分母 = scope 内工单总数 + 10，基线口径）。 */
    private StatsDtos.ComplaintSummary complaintSummary(Set<Long> deptScope, boolean admin) {
        QueryWrapper<Complaint> w = new QueryWrapper<Complaint>()
                .select("status", "COUNT(*) AS cnt")
                .groupBy("status");
        if (!admin) {
            if (deptScope.isEmpty()) {
                return new StatsDtos.ComplaintSummary(0, 0, 0, 0, 0, 0);
            }
            w.apply("order_id IN (SELECT id FROM repair_order WHERE dept_id IN (" + joinIds(deptScope) + "))");
        }
        Map<String, Long> byStatus = new HashMap<>();
        long total = 0;
        for (Map<String, Object> row : complaintMapper.selectMaps(w)) {
            long c = ((Number) row.get("cnt")).longValue();
            byStatus.put(String.valueOf(row.get("status")), c);
            total += c;
        }

        QueryWrapper<RepairOrder> orderW = new QueryWrapper<RepairOrder>();
        if (!admin) {
            orderW.apply("dept_id IN (" + joinIds(deptScope) + ")");
        }
        long orderTotal = orderMapper.selectCount(orderW);
        double rate = total * 100.0 / (orderTotal + 10);

        return new StatsDtos.ComplaintSummary(total,
                byStatus.getOrDefault("PENDING", 0L),
                byStatus.getOrDefault("PROCESSING", 0L),
                byStatus.getOrDefault("RESOLVED", 0L),
                byStatus.getOrDefault("REJECTED", 0L),
                Math.round(rate * 10) / 10.0);
    }

    private String joinIds(Set<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }
}
