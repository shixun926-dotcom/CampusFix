package com.campusfix.common.enums;

import lombok.Getter;

/**
 * 工单流转动作。与《系统设计说明书》§10.1 的状态转换矩阵一一对应。
 *
 * <p>{@code label} 只用于提示文案与流转日志备注，不参与逻辑判断。</p>
 */
@Getter
public enum OrderAction {

    REVIEW_PASS("审核通过"),
    REVIEW_REJECT("审核驳回"),
    ASSIGN("指派"),
    TRANSFER("转派"),
    GRAB("抢单"),
    ACCEPT("确认接单"),
    RETURN("退单"),
    SCHEDULE_CONFIRM("确认预约"),
    RESCHEDULE_REJECT("拒绝改约"),
    SUSPEND("挂起"),
    RESUME("恢复"),
    SUBMIT_RESULT("提交维修结果"),
    CONFIRM("学生确认完成"),
    AUTO_CONFIRM("超时自动验收"),
    REWORK("申请返修"),
    UNREPAIRABLE("无法维修"),
    CANCEL("取消工单");

    private final String label;

    OrderAction(String label) {
        this.label = label;
    }
}
