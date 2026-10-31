package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusfix.common.enums.ComplaintStatus;
import com.campusfix.common.enums.ComplaintType;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.dto.EvaluationDtos;
import com.campusfix.order.entity.Complaint;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.mapper.ComplaintMapper;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.service.DeptService;
import com.campusfix.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 投诉：报修人提交 → 管理员受理 → 成立/不成立。
 *
 * <p>扣分口径写死在状态上而不是处理意见里：{@code RESOLVED}（成立）才给维修工扣分，
 * {@code REJECTED}（不成立）不扣。这样「投诉成立率」和「信誉分变动」永远对得上，
 * 不会出现「意见写了成立但状态是驳回」这种账实不符。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintService {

    /** 投诉成立时维修工扣分幅度，与基线 §8.2 一致 */
    private static final int PENALTY = 10;

    private final ComplaintMapper complaintMapper;
    private final RepairOrderMapper orderMapper;
    private final SysUserMapper userMapper;
    private final DeptService deptService;
    private final OrderQueryService queryService;
    private final UserService userService;

    // ------------------------------------------------------------------ 写

    /**
     * 提交投诉。工单必须已进入「待确认」或「已完成」——服务发生了才谈得上投诉。
     */
    @Transactional
    public EvaluationDtos.ComplaintVO submit(Long orderId, EvaluationDtos.ComplaintRequest req, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);

        if (!Objects.equals(order.getReporterId(), me.getId())) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "只有本单报修人可以投诉");
        }
        if (!order.isStatus(OrderStatus.PENDING_CONFIRM) && !order.isStatus(OrderStatus.COMPLETED)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "维修完成或待确认的工单才能投诉");
        }
        if (!ComplaintType.isValid(req.type())) {
            throw new BizException(ResultCode.PARAM_INVALID, "投诉类型不合法");
        }

        // 同一单只允许一条未终结的投诉，避免刷单式投诉堆积
        Long open = complaintMapper.selectCount(new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getOrderId, orderId)
                .in(Complaint::getStatus, ComplaintStatus.PENDING.name(), ComplaintStatus.PROCESSING.name()));
        if (open != null && open > 0) {
            throw new BizException(ResultCode.COMPLAINT_EXISTS, "该工单已有一条待处理的投诉，请等待处理结果");
        }

        Complaint c = new Complaint();
        c.setOrderId(orderId);
        c.setStudentId(order.getReporterId());
        c.setRepairerId(order.getAssigneeId());
        c.setType(req.type());
        c.setContent(req.content().trim());
        c.setStatus(ComplaintStatus.PENDING.name());
        complaintMapper.insert(c);

        String assignee = order.getAssigneeId() == null ? "（无维修人）" : nameOf(order.getAssigneeId());
        log.info("工单 {} 收到投诉 #{}，被投诉人 {}", order.getOrderNo(), c.getId(), assignee);

        return toVO(c, order, true);
    }

    /**
     * 处理投诉。成立则扣维修工 {@value #PENALTY} 分。
     */
    @Transactional
    public EvaluationDtos.ComplaintVO handle(Long id, EvaluationDtos.ComplaintHandleRequest req, LoginUser me) {
        Complaint c = complaintMapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "投诉不存在");
        }
        ComplaintStatus current = ComplaintStatus.of(c.getStatus());
        if (current.isClosed()) {
            throw new BizException(ResultCode.COMPLAINT_HANDLED, "该投诉已处理完毕，不能重复处理");
        }

        ComplaintStatus target = ComplaintStatus.of(req.status());
        if (target == ComplaintStatus.PENDING) {
            throw new BizException(ResultCode.PARAM_INVALID, "处理结果只能是处理中 / 成立 / 不成立");
        }
        if (target.isClosed() && !StringUtils.hasText(req.result())) {
            throw new BizException(ResultCode.PARAM_INVALID, "结论为成立或不成立时必须填写处理意见");
        }

        boolean resolvedNow = target == ComplaintStatus.RESOLVED;

        c.setStatus(target.name());
        c.setHandlerId(me.getId());
        c.setHandleResult(StringUtils.hasText(req.result()) ? req.result().trim() : null);
        c.setHandledAt(LocalDateTime.now());
        complaintMapper.updateById(c);

        if (resolvedNow && c.getRepairerId() != null) {
            RepairOrder order = orderMapper.selectById(c.getOrderId());
            userService.adjustCredit(c.getRepairerId(), -PENALTY,
                    "投诉成立（投诉 #" + c.getId() + "，工单 "
                            + (order == null ? "-" : order.getOrderNo()) + "）",
                    c.getOrderId(), me.getId());
            log.info("投诉 #{} 成立，维修工 {} 扣 {} 分", c.getId(), c.getRepairerId(), PENALTY);
        }

        return toVO(c, orderMapper.selectById(c.getOrderId()), true);
    }

    // ------------------------------------------------------------------ 读

    /**
     * 投诉列表。ADMIN 看全部；MANAGER 看本部门及下级部门工单的投诉。
     */
    public EvaluationDtos.ComplaintPageVO page(EvaluationDtos.ComplaintQuery query, LoginUser me) {
        LambdaQueryWrapper<Complaint> w = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getStatus())) {
            w.eq(Complaint::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getType())) {
            w.eq(Complaint::getType, query.getType());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            // 工单号 / 投诉内容 / 报修人姓名 三选一命中即可
            w.and(x -> x.inSql(Complaint::getOrderId,
                            "SELECT id FROM repair_order WHERE order_no LIKE '%" + escape(kw) + "%'")
                    .or().like(Complaint::getContent, kw)
                    .or().inSql(Complaint::getStudentId,
                            "SELECT id FROM sys_user WHERE real_name LIKE '%" + escape(kw) + "%'"));
        }

        // 部门分片：MANAGER 只看本部门及下级
        Set<String> roles = me.getRoles() == null ? Set.of() : Set.copyOf(me.getRoles());
        if (!roles.contains(RoleCode.ADMIN)) {
            Long deptId = me.getDeptId();
            Set<Long> depts = deptId == null ? Set.of() : deptService.getDescendantIds(deptId);
            if (depts.isEmpty()) {
                // 无部门归属的账号（如某些后勤岗）默认只看自己提的
                w.eq(Complaint::getStudentId, me.getId());
            } else {
                String ids = depts.stream().map(String::valueOf).collect(Collectors.joining(","));
                w.inSql(Complaint::getOrderId, "SELECT id FROM repair_order WHERE dept_id IN (" + ids + ")");
            }
        }
        w.orderByDesc(Complaint::getCreatedAt);

        int page = Math.max(1, query.getPage() == null ? 1 : query.getPage());
        int size = Math.min(Math.max(1, query.getSize() == null ? 20 : query.getSize()), 100);
        IPage<Complaint> p = complaintMapper.selectPage(new Page<>(page, size), w);

        List<EvaluationDtos.ComplaintVO> list = p.getRecords().isEmpty()
                ? List.of()
                : toVOList(p.getRecords(), false);

        EvaluationDtos.ComplaintPageVO vo = new EvaluationDtos.ComplaintPageVO();
        vo.setPage(new PageResult<>(list, p.getTotal(), p.getCurrent(), p.getSize()));
        vo.setPendingCount(countByStatus(ComplaintStatus.PENDING));
        vo.setProcessingCount(countByStatus(ComplaintStatus.PROCESSING));
        vo.setResolvedCount(countByStatus(ComplaintStatus.RESOLVED));
        vo.setRejectedCount(countByStatus(ComplaintStatus.REJECTED));
        vo.setComplaintRate(complaintRate());
        vo.setTypes(java.util.Arrays.stream(ComplaintType.values())
                .map(t -> t.name() + ":" + t.getLabel()).toList());
        return vo;
    }

    /**
     * 某张工单的投诉记录，供详情抽屉展示。
     */
    public List<EvaluationDtos.ComplaintVO> listByOrder(Long orderId, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!queryService.canView(order, me)) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "无权查看该工单的投诉");
        }
        List<Complaint> list = complaintMapper.selectList(new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getOrderId, orderId)
                .orderByDesc(Complaint::getCreatedAt));
        return toVOList(list, true);
    }

    /* ------------------------------------------------------------------ 统计与小工具 */

    /** 投诉率 = 投诉数 / (总工单数 + 10)，小样本平滑口径（基线 A22） */
    private BigDecimal complaintRate() {
        long complaints = complaintMapper.selectCount(null);
        Long orders = orderMapper.selectCount(null);
        long denominator = (orders == null ? 0 : orders) + 10;
        return BigDecimal.valueOf(complaints)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private long countByStatus(ComplaintStatus status) {
        Long n = complaintMapper.selectCount(
                new LambdaQueryWrapper<Complaint>().eq(Complaint::getStatus, status.name()));
        return n == null ? 0 : n;
    }

    private List<EvaluationDtos.ComplaintVO> toVOList(List<Complaint> list, boolean withTitle) {
        List<Long> orderIds = list.stream().map(Complaint::getOrderId).distinct().toList();
        Map<Long, RepairOrder> orders = orderIds.isEmpty() ? Map.of()
                : orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>().in(RepairOrder::getId, orderIds))
                        .stream().collect(Collectors.toMap(RepairOrder::getId, Function.identity(), (a, b) -> a));

        List<Long> userIds = list.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.getStudentId(), c.getRepairerId(), c.getHandlerId()))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        return list.stream().map(c -> {
            EvaluationDtos.ComplaintVO vo = fill(c, orders.get(c.getOrderId()), names);
            if (!withTitle) {
                vo.setOrderTitle(null);
            }
            return vo;
        }).toList();
    }

    private EvaluationDtos.ComplaintVO toVO(Complaint c, RepairOrder order, boolean withTitle) {
        Map<Long, String> names = new java.util.HashMap<>();
        for (Long uid : new Long[]{c.getStudentId(), c.getRepairerId(), c.getHandlerId()}) {
            if (uid != null && !names.containsKey(uid)) {
                names.put(uid, nameOf(uid));
            }
        }
        EvaluationDtos.ComplaintVO vo = fill(c, order, names);
        if (!withTitle) {
            vo.setOrderTitle(null);
        }
        return vo;
    }

    private EvaluationDtos.ComplaintVO fill(Complaint c, RepairOrder order, Map<Long, String> names) {
        EvaluationDtos.ComplaintVO vo = new EvaluationDtos.ComplaintVO();
        vo.setId(c.getId());
        vo.setOrderId(c.getOrderId());
        vo.setOrderNo(order == null ? null : order.getOrderNo());
        vo.setOrderTitle(order == null ? null : order.getTitle());
        vo.setStudentId(c.getStudentId());
        vo.setStudentName(names.get(c.getStudentId()));
        vo.setRepairerId(c.getRepairerId());
        vo.setRepairerName(c.getRepairerId() == null ? null : names.get(c.getRepairerId()));
        vo.setType(c.getType());
        vo.setTypeLabel(ComplaintType.labelOf(c.getType()));
        vo.setContent(c.getContent());
        vo.setStatus(c.getStatus());
        vo.setStatusLabel(ComplaintStatus.of(c.getStatus()).getLabel());
        vo.setHandlerId(c.getHandlerId());
        vo.setHandlerName(c.getHandlerId() == null ? null : names.get(c.getHandlerId()));
        vo.setHandleResult(c.getHandleResult());
        vo.setHandledAt(c.getHandledAt());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = userMapper.selectById(userId);
        return u == null ? null : u.getRealName();
    }

    /** 关键字进 SQL 前转义单引号与反斜杠，避免拼串注入 */
    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "''");
    }
}
