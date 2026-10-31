package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.dto.EvaluationDtos;
import com.campusfix.order.entity.Evaluation;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.mapper.EvaluationMapper;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.service.SystemConfigService;
import com.campusfix.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评价：三维评分 + 加权总分。
 *
 * <p>三条硬规则（基线 §8.2、§9.3）：</p>
 * <ol>
 *   <li>只有<b>本单报修人</b>能评价，且工单必须已「已完成」——不是看角色，是看归属；</li>
 *   <li>一单只能评价一次，由 {@code evaluation.uk_order} 与业务校验双重保证；</li>
 *   <li>评价成功给维修工 <b>+1</b> 信誉分（上限 100），走 {@link UserService#adjustCredit} 写流水。</li>
 * </ol>
 *
 * <p>总分在落库时算好：权重可能被管理员调整，历史评价不应随之变动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    /** 权重参数键，与 sys_config 一致 */
    private static final String KEY_SPEED = "evaluation.speed-weight";
    private static final String KEY_ATTITUDE = "evaluation.attitude-weight";
    private static final String KEY_EFFECT = "evaluation.effect-weight";

    private static final BigDecimal DEFAULT_SPEED = new BigDecimal("0.34");
    private static final BigDecimal DEFAULT_ATTITUDE = new BigDecimal("0.33");
    private static final BigDecimal DEFAULT_EFFECT = new BigDecimal("0.33");

    /** 评价后给维修工的信誉加分 */
    private static final int CREDIT_BONUS = 1;

    private final EvaluationMapper evaluationMapper;
    private final RepairOrderMapper orderMapper;
    private final SysUserMapper userMapper;
    private final SystemConfigService configService;
    private final OrderQueryService queryService;
    private final UserService userService;

    // ------------------------------------------------------------------ 写

    /**
     * 提交评价。
     */
    @Transactional
    public EvaluationDtos.EvaluationVO evaluate(Long orderId, EvaluationDtos.EvaluateRequest req, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);

        if (!Objects.equals(order.getReporterId(), me.getId())) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "只有本单报修人可以评价");
        }
        if (!order.isStatus(com.campusfix.common.enums.OrderStatus.COMPLETED)) {
            throw new BizException(ResultCode.ORDER_NOT_FINISHED, "工单完成后才能评价");
        }
        if (order.getAssigneeId() == null) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "该工单没有维修人，无法评价");
        }

        Long exists = evaluationMapper.selectCount(
                new LambdaQueryWrapper<Evaluation>().eq(Evaluation::getOrderId, orderId));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.REPEAT_EVALUATION, "该工单已评价，不能重复提交");
        }

        BigDecimal totalScore = weightedScore(req.speedScore(), req.attitudeScore(), req.effectScore());

        Evaluation e = new Evaluation();
        e.setOrderId(orderId);
        e.setStudentId(order.getReporterId());
        e.setRepairerId(order.getAssigneeId());
        e.setSpeedScore(req.speedScore());
        e.setAttitudeScore(req.attitudeScore());
        e.setEffectScore(req.effectScore());
        e.setTotalScore(totalScore);
        e.setContent(StringUtils.hasText(req.content()) ? req.content().trim() : null);
        e.setAnonymous(Boolean.TRUE.equals(req.anonymous()) ? 1 : 0);

        try {
            evaluationMapper.insert(e);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 并发重复提交，靠 uk_order 兜底
            throw new BizException(ResultCode.REPEAT_EVALUATION, "该工单已评价，不能重复提交");
        }

        // 评价带来的信誉加分：不是报修人给的奖励，而是「完成闭环」的正向记录
        userService.adjustCredit(order.getAssigneeId(), CREDIT_BONUS,
                "工单评价完成（工单 " + order.getOrderNo() + "）", orderId, null);

        log.info("工单 {} 评价完成：总分 {}，维修人 {} +{} 信誉分",
                order.getOrderNo(), totalScore, order.getAssigneeId(), CREDIT_BONUS);

        return toVO(e, order, order.getReporterId(), order.getAssigneeId(), true);
    }

    /**
     * 加权总分 = (速度×w1 + 态度×w2 + 效果×w3) / (w1+w2+w3)。
     *
     * <p>除以权重和是为了容错：管理员把权重配成 1:1:1 时结果仍是平均分，
     * 不会算出一个 15 分制的怪数。</p>
     */
    public BigDecimal weightedScore(int speed, int attitude, int effect) {
        BigDecimal w1 = positive(configService.getDecimal(KEY_SPEED, DEFAULT_SPEED), DEFAULT_SPEED);
        BigDecimal w2 = positive(configService.getDecimal(KEY_ATTITUDE, DEFAULT_ATTITUDE), DEFAULT_ATTITUDE);
        BigDecimal w3 = positive(configService.getDecimal(KEY_EFFECT, DEFAULT_EFFECT), DEFAULT_EFFECT);

        BigDecimal sum = w1.add(w2).add(w3);
        if (sum.compareTo(BigDecimal.ZERO) <= 0) {
            // 三个权重全配成 0/负数，退回等权，避免除零
            w1 = w2 = w3 = BigDecimal.ONE;
            sum = new BigDecimal("3");
        }

        BigDecimal weighted = w1.multiply(BigDecimal.valueOf(speed))
                .add(w2.multiply(BigDecimal.valueOf(attitude)))
                .add(w3.multiply(BigDecimal.valueOf(effect)));

        return weighted.divide(sum, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal positive(BigDecimal v, BigDecimal fallback) {
        return v == null || v.compareTo(BigDecimal.ZERO) < 0 ? fallback : v;
    }

    // ------------------------------------------------------------------ 读

    /**
     * 工单评价，未评价返回 null。
     */
    public EvaluationDtos.EvaluationVO getByOrder(Long orderId, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!queryService.canView(order, me)) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "无权查看该工单的评价");
        }
        Evaluation e = evaluationMapper.selectOne(
                new LambdaQueryWrapper<Evaluation>().eq(Evaluation::getOrderId, orderId).last("LIMIT 1"));
        if (e == null) {
            return null;
        }
        return toVO(e, order, order.getReporterId(), order.getAssigneeId(), canSeeRealName(me, e.getStudentId()));
    }

    /**
     * 维修工收到的评价列表。
     */
    public PageResult<EvaluationDtos.EvaluationVO> pageByRepairer(Long repairerId, int page, int size, LoginUser me) {
        IPage<Evaluation> p = evaluationMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)),
                new LambdaQueryWrapper<Evaluation>()
                        .eq(Evaluation::getRepairerId, repairerId)
                        .orderByDesc(Evaluation::getCreatedAt));

        if (p.getRecords().isEmpty()) {
            return new PageResult<>(List.of(), 0, p.getCurrent(), p.getSize());
        }

        List<Long> orderIds = p.getRecords().stream().map(Evaluation::getOrderId).toList();
        Map<Long, RepairOrder> orders = orderMapper.selectList(
                        new LambdaQueryWrapper<RepairOrder>().in(RepairOrder::getId, orderIds))
                .stream().collect(Collectors.toMap(RepairOrder::getId, Function.identity(), (a, b) -> a));

        String repairerName = nameOf(repairerId);
        List<EvaluationDtos.EvaluationVO> list = p.getRecords().stream()
                .map(e -> {
                    RepairOrder o = orders.get(e.getOrderId());
                    // 逐条判断：涉及「自己评价自己」这种边角情况也不会错
                    boolean realName = canSeeRealName(me, e.getStudentId());
                    return toVO(e, o, e.getStudentId(), repairerId, realName, repairerName);
                })
                .toList();
        return new PageResult<>(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /**
     * 维修工评价汇总。没有评价时全部返回 0，不做除零。
     */
    public EvaluationDtos.EvaluationSummaryVO summary(Long repairerId) {
        Map<String, Object> row = evaluationMapper.summaryOf(repairerId);

        EvaluationDtos.EvaluationSummaryVO vo = new EvaluationDtos.EvaluationSummaryVO();
        vo.setRepairerId(repairerId);
        vo.setRepairerName(nameOf(repairerId));
        vo.setTotal(intOf(row, "cnt"));
        vo.setAvgTotal(scale(row, "avgTotal"));
        vo.setAvgSpeed(scale(row, "avgSpeed"));
        vo.setAvgAttitude(scale(row, "avgAttitude"));
        vo.setAvgEffect(scale(row, "avgEffect"));

        int total = vo.getTotal();
        if (total == 0) {
            vo.setGoodRate(BigDecimal.ZERO);
        } else {
            Long good = evaluationMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                    .eq(Evaluation::getRepairerId, repairerId)
                    .ge(Evaluation::getTotalScore, new BigDecimal("4.5")));
            vo.setGoodRate(BigDecimal.valueOf(good == null ? 0 : good)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP));
        }
        return vo;
    }

    /* ------------------------------------------------------------------ 小工具 */

    /**
     * 谁能看到评价人真实姓名：管理员与后勤（处理投诉时需要知道是谁提的）。
     * 维修工与其他人只看到「匿名用户」——但匿名开关只影响展示，数据库里始终存真实 ID。
     */
    private boolean canSeeRealName(LoginUser me, Long studentId) {
        if (me == null) {
            return false;
        }
        // 本人始终看得见自己写的评价（哪怕勾了匿名）——匿名是对别人匿名，不是对自己
        if (studentId != null && Objects.equals(me.getId(), studentId)) {
            return true;
        }
        Set<String> roles = me.getRoles() == null ? Set.of() : Set.copyOf(me.getRoles());
        return roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.MANAGER);
    }

    private EvaluationDtos.EvaluationVO toVO(Evaluation e, RepairOrder o, Long studentId, Long repairerId,
                                             boolean realName) {
        return toVO(e, o, studentId, repairerId, realName, nameOf(repairerId));
    }

    private EvaluationDtos.EvaluationVO toVO(Evaluation e, RepairOrder o, Long studentId, Long repairerId,
                                             boolean realName, String repairerName) {
        EvaluationDtos.EvaluationVO vo = new EvaluationDtos.EvaluationVO();
        vo.setId(e.getId());
        vo.setOrderId(e.getOrderId());
        vo.setOrderNo(o == null ? null : o.getOrderNo());
        vo.setStudentId(e.getStudentId());
        // 匿名时后端直接返回脱敏名称，不把真名交给前端再遮挡
        boolean hide = e.getAnonymous() != null && e.getAnonymous() == 1 && !realName;
        vo.setStudentName(hide ? "匿名用户" : nameOf(studentId));
        vo.setRepairerId(repairerId);
        vo.setRepairerName(repairerName);
        vo.setSpeedScore(e.getSpeedScore());
        vo.setAttitudeScore(e.getAttitudeScore());
        vo.setEffectScore(e.getEffectScore());
        vo.setTotalScore(e.getTotalScore());
        vo.setContent(e.getContent());
        vo.setAnonymous(e.getAnonymous() != null && e.getAnonymous() == 1);
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = userMapper.selectById(userId);
        return u == null ? null : u.getRealName();
    }

    private int intOf(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private BigDecimal scale(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        if (v instanceof BigDecimal d) {
            return d.setScale(2, RoundingMode.HALF_UP);
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
