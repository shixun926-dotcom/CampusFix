package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.file.FileStorageService;
import com.campusfix.order.dto.EvaluationDtos;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.dto.ScheduleDtos;
import com.campusfix.common.enums.ComplaintStatus;
import com.campusfix.order.entity.Complaint;
import com.campusfix.order.entity.Evaluation;
import com.campusfix.order.entity.RepairCategory;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.entity.RepairOrderImage;
import com.campusfix.order.entity.RepairOrderLog;
import com.campusfix.order.mapper.ComplaintMapper;
import com.campusfix.order.mapper.EvaluationMapper;
import com.campusfix.order.mapper.RepairCategoryMapper;
import com.campusfix.order.mapper.RepairOrderImageMapper;
import com.campusfix.order.mapper.RepairOrderLogMapper;
import com.campusfix.order.entity.RepairRecord;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.order.mapper.RepairRecordMapper;
import com.campusfix.order.statemachine.OrderStateMachine;
import com.campusfix.order.statemachine.Transition;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysDeptMapper;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工单查询：列表、抢单池、详情、时间轴，以及按角色的数据可见范围。
 *
 * <p>数据权限规则（《系统设计说明书》§3.3 与基线 A5）：</p>
 * <ul>
 *   <li>ADMIN：全部；</li>
 *   <li>MANAGER：本部门及其下级部门的工单（多部门分片）；</li>
 *   <li>REPAIRER：自己名下的 + 自己报修的；抢单池另走 {@code scope=pool}；</li>
 *   <li>STUDENT：仅自己报修的。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final RepairOrderMapper orderMapper;
    private final RepairOrderLogMapper logMapper;
    private final RepairOrderImageMapper imageMapper;
    private final RepairRecordMapper recordMapper;
    private final RepairCategoryMapper categoryMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final DeptService deptService;
    private final FileStorageService fileStorageService;
    private final OrderStateMachine stateMachine;
    private final EvaluationMapper evaluationMapper;
    private final ComplaintMapper complaintMapper;

    public PageResult<OrderDtos.OrderVO> page(OrderDtos.Query query, LoginUser me) {
        LambdaQueryWrapper<RepairOrder> w = new LambdaQueryWrapper<>();

        String scope = StringUtils.hasText(query.getScope()) ? query.getScope() : "mine";
        if ("pool".equals(scope)) {
            // 抢单池：全部待处理工单（基线 A5：维修工可看全部待维修单自行判断）
            requireGrabPermission(me);
            w.eq(RepairOrder::getStatus, OrderStatus.PENDING.name());
        } else {
            applyDataScope(w, me);
        }

        if (StringUtils.hasText(query.getStatus())) {
            List<String> statuses = parseStatuses(query.getStatus());
            if (!statuses.isEmpty()) {
                w.in(RepairOrder::getStatus, statuses);
            }
        }
        if (query.getCategoryId() != null) {
            w.eq(RepairOrder::getCategoryId, query.getCategoryId());
        }
        if (query.getBuildingId() != null) {
            w.eq(RepairOrder::getBuildingId, query.getBuildingId());
        }
        if (query.getDeptId() != null) {
            w.eq(RepairOrder::getDeptId, query.getDeptId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            w.and(q -> q.like(RepairOrder::getTitle, kw)
                    .or().like(RepairOrder::getOrderNo, kw)
                    .or().like(RepairOrder::getLocationPath, kw));
        }
        if (query.getStartTime() != null) {
            w.ge(RepairOrder::getCreatedAt, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            w.le(RepairOrder::getCreatedAt, query.getEndTime());
        }
        // 抢单池按「优先级高的、时间早的」排在前面；其它列表按时间倒序
        if ("pool".equals(scope)) {
            w.orderByDesc(RepairOrder::getPriority).orderByAsc(RepairOrder::getCreatedAt);
        } else {
            w.orderByDesc(RepairOrder::getCreatedAt);
        }

        long size = Math.min(Math.max(query.getSize(), 1), 100);
        IPage<RepairOrder> page = orderMapper.selectPage(new Page<>(Math.max(query.getPage(), 1), size), w);
        List<OrderDtos.OrderVO> list = toVOList(page.getRecords(), me);
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public OrderDtos.OrderDetailVO detail(Long id, LoginUser me) {
        RepairOrder order = getOrder(id);
        if (!canView(order, me)) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "无权查看该工单");
        }
        OrderDtos.OrderDetailVO vo = new OrderDtos.OrderDetailVO();
        fill(vo, order, me);

        Set<Long> ids = new HashSet<>();
        if (order.getReporterId() != null) {
            ids.add(order.getReporterId());
        }
        if (order.getAssigneeId() != null) {
            ids.add(order.getAssigneeId());
        }
        fillNames(vo, order, userMap(ids), categoryMap(), deptMap());

        vo.setDescription(order.getDescription());
        vo.setFacilityId(order.getFacilityId());
        vo.setRejectReason(order.getRejectReason());
        vo.setReviewAt(order.getReviewAt());
        vo.setArrivedAt(order.getArrivedAt());
        vo.setStartedAt(order.getStartedAt());
        vo.setConfirmedAt(order.getConfirmedAt());

        List<RepairOrderImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<RepairOrderImage>()
                        .eq(RepairOrderImage::getOrderId, id)
                        .orderByAsc(RepairOrderImage::getSort));
        vo.setImages(images.stream()
                .map(img -> new OrderDtos.ImageVO(img.getId(), img.getStage(), img.getObjectKey(),
                        fileStorageService.getUrl(img.getObjectKey()), img.getSort()))
                .toList());

        RepairRecord record = recordMapper.selectOne(new LambdaQueryWrapper<RepairRecord>()
                .eq(RepairRecord::getOrderId, id).last("LIMIT 1"));
        if (record != null) {
            vo.setRecord(toRecordVO(record));
        }

        // 评价与投诉：详情页一次性带出，省掉前端再发两次请求
        Evaluation evaluation = evaluationMapper.selectOne(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getOrderId, id).last("LIMIT 1"));
        boolean reporter = me != null && java.util.Objects.equals(order.getReporterId(), me.getId());
        boolean completed = order.isStatus(OrderStatus.COMPLETED);
        vo.setEvaluation(evaluation == null ? null : toEvaluationVO(evaluation, order, me));
        vo.setCanEvaluate(reporter && completed && evaluation == null);

        long complaintCount = complaintMapper.selectCount(
                new LambdaQueryWrapper<Complaint>().eq(Complaint::getOrderId, id));
        long openComplaints = complaintMapper.selectCount(new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getOrderId, id)
                .in(Complaint::getStatus, ComplaintStatus.PENDING.name(), ComplaintStatus.PROCESSING.name()));
        vo.setComplaintCount((int) complaintCount);
        vo.setHasOpenComplaint(openComplaints > 0);

        // 评价 / 投诉不是状态流转，状态机里没有对应的边，单独追加到动作列表
        List<String> actions = new ArrayList<>(vo.getActions() == null ? List.of() : vo.getActions());
        if (Boolean.TRUE.equals(vo.getCanEvaluate())) {
            actions.add("EVALUATE");
        }
        if (reporter && openComplaints == 0
                && (order.isStatus(OrderStatus.PENDING_CONFIRM) || completed)) {
            actions.add("COMPLAINT");
        }
        vo.setActions(actions);

        return vo;
    }

    /**
     * 详情里的评价转换。匿名只对非管理员/后勤脱敏 —— 他们处理投诉时需要知道是谁提的。
     */
    private EvaluationDtos.EvaluationVO toEvaluationVO(Evaluation e, RepairOrder order, LoginUser me) {
        EvaluationDtos.EvaluationVO vo = new EvaluationDtos.EvaluationVO();
        vo.setId(e.getId());
        vo.setOrderId(e.getOrderId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStudentId(e.getStudentId());

        Set<String> roles = me == null || me.getRoles() == null ? Set.of() : Set.copyOf(me.getRoles());
        // 本人 + 管理员 + 后勤看真名；维修工等只看「匿名用户」
        boolean realName = (me != null && java.util.Objects.equals(me.getId(), e.getStudentId()))
                || roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.MANAGER);
        boolean hide = e.getAnonymous() != null && e.getAnonymous() == 1 && !realName;

        SysUser student = e.getStudentId() == null ? null : userMapper.selectById(e.getStudentId());
        SysUser repairer = e.getRepairerId() == null ? null : userMapper.selectById(e.getRepairerId());
        vo.setStudentName(hide ? "匿名用户" : (student == null ? null : student.getRealName()));
        vo.setRepairerId(e.getRepairerId());
        vo.setRepairerName(repairer == null ? null : repairer.getRealName());
        vo.setSpeedScore(e.getSpeedScore());
        vo.setAttitudeScore(e.getAttitudeScore());
        vo.setEffectScore(e.getEffectScore());
        vo.setTotalScore(e.getTotalScore());
        vo.setContent(e.getContent());
        vo.setAnonymous(e.getAnonymous() != null && e.getAnonymous() == 1);
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public List<OrderDtos.LogVO> logs(Long id, LoginUser me) {
        RepairOrder order = getOrder(id);
        if (!canView(order, me)) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "无权查看该工单");
        }
        List<RepairOrderLog> logs = logMapper.selectList(
                new LambdaQueryWrapper<RepairOrderLog>()
                        .eq(RepairOrderLog::getOrderId, id)
                        .orderByAsc(RepairOrderLog::getCreatedAt)
                        .orderByAsc(RepairOrderLog::getId));

        Set<Long> userIds = logs.stream().map(RepairOrderLog::getOperatorId)
                .filter(uid -> uid != null && uid > 0).collect(Collectors.toSet());
        Map<Long, String> names = userNameMap(userIds);

        return logs.stream().map(l -> new OrderDtos.LogVO(
                l.getId(),
                l.getFromStatus(),
                l.getToStatus(),
                labelOf(l.getFromStatus()),
                labelOf(l.getToStatus()),
                actionLabelOf(l.getAction()),
                names.getOrDefault(l.getOperatorId(), "系统"),
                l.getOperatorRole(),
                l.getRemark(),
                l.getCreatedAt())).toList();
    }

    public RepairOrder getOrder(Long id) {
        RepairOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工单不存在");
        }
        return order;
    }

    // ------------------------------------------------------------------ 权限

    /** 详情/日志可见性：管理员全部、后勤按部门、相关方、维修工可看待处理的（抢单池详情）。 */
    public boolean canView(RepairOrder order, LoginUser me) {
        if (me.hasRole(RoleCode.ADMIN)) {
            return true;
        }
        if (order.getReporterId() != null && order.getReporterId().equals(me.getId())) {
            return true;
        }
        if (order.getAssigneeId() != null && order.getAssigneeId().equals(me.getId())) {
            return true;
        }
        if (me.hasRole(RoleCode.MANAGER)) {
            Set<Long> depts = deptService.getDescendantIds(me.getDeptId());
            return order.getDeptId() != null && depts.contains(order.getDeptId());
        }
        return me.hasRole(RoleCode.REPAIRER) && order.isStatus(OrderStatus.PENDING);
    }

    private void applyDataScope(LambdaQueryWrapper<RepairOrder> w, LoginUser me) {
        if (me.hasRole(RoleCode.ADMIN)) {
            return;
        }
        if (me.hasRole(RoleCode.MANAGER)) {
            Set<Long> depts = deptService.getDescendantIds(me.getDeptId());
            if (depts.isEmpty()) {
                w.eq(RepairOrder::getReporterId, me.getId());
            } else {
                w.in(RepairOrder::getDeptId, depts);
            }
            return;
        }
        if (me.hasRole(RoleCode.REPAIRER)) {
            w.and(q -> q.eq(RepairOrder::getAssigneeId, me.getId())
                    .or().eq(RepairOrder::getReporterId, me.getId()));
            return;
        }
        w.eq(RepairOrder::getReporterId, me.getId());
    }

    private void requireGrabPermission(LoginUser me) {
        if (me.hasRole(RoleCode.REPAIRER) || me.hasRole(RoleCode.ADMIN)) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN, "只有维修人员可以查看抢单池");
    }

    // ------------------------------------------------------------------ 组装

    private List<OrderDtos.OrderVO> toVOList(List<RepairOrder> records, LoginUser me) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        records.forEach(o -> {
            if (o.getReporterId() != null) {
                userIds.add(o.getReporterId());
            }
            if (o.getAssigneeId() != null) {
                userIds.add(o.getAssigneeId());
            }
        });
        Map<Long, SysUser> users = userMap(userIds);
        Map<Long, String> categories = categoryMap();
        Map<Long, String> depts = deptMap();

        return records.stream().map(o -> {
            OrderDtos.OrderVO vo = new OrderDtos.OrderVO();
            fill(vo, o, me);
            fillNames(vo, o, users, categories, depts);
            return vo;
        }).toList();
    }

    /** 名称类字段统一在批量映射后补齐，避免逐单查库。 */
    private void fillNames(OrderDtos.OrderVO vo, RepairOrder o, Map<Long, SysUser> users,
                           Map<Long, String> categories, Map<Long, String> depts) {
        vo.setCategoryName(categories.get(o.getCategoryId()));
        vo.setDeptName(o.getDeptId() == null ? null : depts.get(o.getDeptId()));
        SysUser reporter = users.get(o.getReporterId());
        vo.setReporterName(reporter == null ? null : reporter.getRealName());
        SysUser assignee = users.get(o.getAssigneeId());
        vo.setAssigneeName(assignee == null ? null : assignee.getRealName());
        vo.setAssigneePhone(assignee == null ? null : assignee.getPhone());
    }

    private void fill(OrderDtos.OrderVO vo, RepairOrder o, LoginUser me) {
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setTitle(o.getTitle());
        vo.setStatus(o.getStatus());
        vo.setStatusLabel(labelOf(o.getStatus()));
        vo.setCategoryId(o.getCategoryId());
        vo.setLocationId(o.getLocationId());
        vo.setLocationPath(o.getLocationPath());
        vo.setBuildingId(o.getBuildingId());
        vo.setUrgency(o.getUrgency());
        vo.setPriority(o.getPriority());
        vo.setReporterId(o.getReporterId());
        vo.setReporterPhone(o.getReporterPhone());
        vo.setAssigneeId(o.getAssigneeId());
        vo.setDeptId(o.getDeptId());
        vo.setIsWarranty(o.getIsWarranty());
        vo.setReworkFlag(o.getReworkFlag());
        vo.setReworkCount(o.getReworkCount());
        vo.setAutoAssigned(o.getAutoAssigned());
        vo.setAutoConfirmed(o.getAutoConfirmed() != null && o.getAutoConfirmed() == 1);
        vo.setPlanTime(o.getPlanTime());
        vo.setCreatedAt(o.getCreatedAt());
        vo.setAcceptedAt(o.getAcceptedAt());
        vo.setFinishedAt(o.getFinishedAt());
        vo.setConfirmDeadline(o.getConfirmDeadline());
        vo.setActions(resolveActions(o, me));
    }

    /**
     * 当前登录者在该工单上可执行的动作。
     *
     * <p>只用于前端按钮显隐，真正的校验仍在状态机里做一遍 —— 前端只决定「看不看得见」。</p>
     */
    public List<String> resolveActions(RepairOrder o, LoginUser me) {
        OrderStatus from = o.statusEnum();
        List<String> actions = new ArrayList<>();
        for (Transition t : OrderStateMachine.allTransitions()) {
            if (!t.appliesTo(from) || !t.roleAllowed(me.getRoles())) {
                continue;
            }
            OrderAction action = t.action();
            if ((action == OrderAction.ACCEPT || action == OrderAction.RETURN
                    || action == OrderAction.GRAB)
                    && o.getAssigneeId() != null
                    && !o.getAssigneeId().equals(me.getId())) {
                continue;
            }
            if ((action == OrderAction.CONFIRM || action == OrderAction.REWORK
                    || action == OrderAction.RESCHEDULE_REJECT)
                    && !me.getId().equals(o.getReporterId())) {
                continue;
            }
            if (action == OrderAction.CANCEL && !me.hasPermission("order:cancel")) {
                continue;
            }
            if (action == OrderAction.GRAB && !me.hasPermission("order:grab")) {
                continue;
            }
            if (action == OrderAction.SUBMIT_RESULT || action == OrderAction.SUSPEND
                    || action == OrderAction.RESUME || action == OrderAction.UNREPAIRABLE
                    || action == OrderAction.ACCEPT || action == OrderAction.RETURN) {
                if (o.getAssigneeId() == null || !o.getAssigneeId().equals(me.getId())) {
                    continue;
                }
            }
            actions.add(action.name());
        }
        return actions;
    }

    private ScheduleDtos.RecordVO toRecordVO(RepairRecord r) {
        ScheduleDtos.RecordVO vo = new ScheduleDtos.RecordVO();
        vo.setId(r.getId());
        vo.setOrderId(r.getOrderId());
        vo.setRepairerId(r.getRepairerId());
        vo.setArriveAt(r.getArriveAt());
        vo.setStartAt(r.getStartAt());
        vo.setEndAt(r.getEndAt());
        vo.setContent(r.getContent());
        vo.setMaterial(r.getMaterial());
        vo.setResult(r.getResult());
        vo.setRemark(r.getRemark());
        if (r.getStartAt() != null && r.getEndAt() != null) {
            vo.setDurationMinutes(java.time.Duration.between(r.getStartAt(), r.getEndAt()).toMinutes());
        }
        return vo;
    }

    // ------------------------------------------------------------------ 小工具

    public static String labelOf(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return OrderStatus.of(status).getLabel();
        } catch (IllegalArgumentException e) {
            return status;
        }
    }

    private static String actionLabelOf(String action) {
        if (!StringUtils.hasText(action)) {
            return null;
        }
        if ("CREATE".equals(action)) {
            return "提交报修";
        }
        try {
            return OrderAction.valueOf(action).getLabel();
        } catch (IllegalArgumentException e) {
            return action;
        }
    }

    private static List<String> parseStatuses(String raw) {
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .filter(s -> {
                    try {
                        OrderStatus.of(s);
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .toList();
    }

    private Map<Long, SysUser> userMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a, HashMap::new));
    }

    private Map<Long, String> userNameMap(Collection<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        userMap(ids).forEach((id, u) -> map.put(id, u.getRealName()));
        return map;
    }

    private Map<Long, String> categoryMap() {
        return categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(RepairCategory::getId, RepairCategory::getName, (a, b) -> a));
    }

    private Map<Long, String> deptMap() {
        return deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));
    }
}
