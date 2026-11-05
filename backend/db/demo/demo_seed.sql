-- ============================================================
-- CampusFix 演示账号与演示数据（正式演示 / 汇报使用）
--
-- 用途：为四个角色工作区各生成一个独立演示账号，并配套覆盖该
--       工作区核心功能的业务数据，便于分角色演示与验收。
--
-- 使用：
--   1) 先执行 db/schema.sql（建表），再执行 db/data.sql（基础字典数据）
--   2) mysql --default-character-set=utf8mb4 -uroot -p < db/demo/demo_seed.sql
--
-- 幂等性：脚本开头会删除上一轮演示数据（仅限演示 ID 段与演示账号），
--         可反复执行，不会影响 data.sql 的基础数据与真实业务数据。
--
-- 演示账号（密码统一 Cf@123456，且不强制改密，便于直接演示）：
--   9001  demo_admin  系统管理员  林振华
--   9002  demo_mgr    后勤管理员  王海涛   后勤保障处（可见下属全部维修组）
--   9003  demo_rep    维修人员    张建国   水电维修组
--   9004  demo_stu    学生        陈晓     计算机 2026-1 班
--
-- 数据规模：20 张工单（覆盖 10 种状态）、预约 / 维修记录 / 评价 /
--           投诉 / 延期申请 / 聊天 / 通知 / 信誉流水 / 操作日志 /
--           登录日志 / 信息更改申请 / 高频故障预警。
--
-- 删除演示数据：执行 db/demo/demo_cleanup.sql（或仅重跑本脚本前的清理段）。
-- ============================================================

USE campusfix;
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 0. 清理上一轮演示数据（仅演示 ID 段 9001–9099 与 4 个演示账号）
-- ------------------------------------------------------------
DELETE FROM notification            WHERE receiver_id BETWEEN 9001 AND 9004;
DELETE FROM chat_message            WHERE sender_id BETWEEN 9001 AND 9004 OR receiver_id BETWEEN 9001 AND 9004;
DELETE FROM complaint               WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM evaluation              WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM order_extension_request WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM repair_record           WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM repair_schedule         WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM repair_order_image      WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM repair_order_log        WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM fault_alert             WHERE id BETWEEN 9001 AND 9099;
DELETE FROM profile_change_request  WHERE applicant_id BETWEEN 9001 AND 9004;
DELETE FROM sys_credit_log          WHERE user_id BETWEEN 9001 AND 9004;
DELETE FROM sys_oper_log            WHERE user_id BETWEEN 9001 AND 9004;
DELETE FROM sys_login_log           WHERE user_id BETWEEN 9001 AND 9004;
DELETE FROM repair_order            WHERE id BETWEEN 9001 AND 9099;
DELETE FROM sys_user_role           WHERE user_id BETWEEN 9001 AND 9004;
DELETE FROM sys_user                WHERE id BETWEEN 9001 AND 9004;

-- ------------------------------------------------------------
-- 1. 演示账号（密码 Cf@123456；pwd_reset_flag=0 表示不强制改密）
-- ------------------------------------------------------------
SET @PWD = '$2b$10$H36cH3AlKv..rd9XSydhqOOphTw.ZADBBbA5M0/NzkPYG64iMlvd6';

INSERT INTO sys_user
(id, username, password, real_name, student_no, class_name, staff_no, dept_id, phone,
 credit_score, status, pwd_reset_flag, last_login_at, created_at) VALUES
(9001, 'demo_admin', @PWD, '林振华', NULL, NULL, 'D0001', 1, '13800009001', 100, 1, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(9002, 'demo_mgr',   @PWD, '王海涛', NULL, NULL, 'D0002', 1, '13800009002', 100, 1, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(9003, 'demo_rep',   @PWD, '张建国', NULL, NULL, 'D0003', 2, '13900009003',  90, 1, 0, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(9004, 'demo_stu',   @PWD, '陈晓', 'D2026099', '计算机 2026-1 班', NULL, NULL, '13700009004', 91, 1, 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 120 DAY));

-- 角色绑定：4=ADMIN 3=MANAGER 2=REPAIRER 1=STUDENT
INSERT INTO sys_user_role (user_id, role_id) VALUES
(9001, 4), (9002, 3), (9003, 2), (9004, 1);

-- ------------------------------------------------------------
-- 2. 工单（20 张，覆盖 10 种状态；先插入公共字段，再按状态补齐生命周期字段）
--    类别 → 部门：水电1→2 网络2→4 空调3→3 门窗4→5 照明5→2 家具6→5 公共7→5 卫生8→2 其他9→5
-- ------------------------------------------------------------
INSERT INTO repair_order
(id, order_no, reporter_id, reporter_phone, title, description, category_id, facility_id,
 location_id, location_path, building_id, urgency, priority, status, dept_id, created_at) VALUES
-- 演示学生：完整生命周期
(9001, 'DM260001', 9004, '13700009004', '301 宿舍空调开机跳闸', '空调一开机整层就跳闸，配电箱空开复位后又跳，怀疑压缩机短路。', 3, NULL, 13, '主校区 / 宿舍区 / 2号宿舍楼 / 3层 / 301', 5, 3, 7, 'PENDING_REVIEW', 3, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9002, 'DM260002', 9004, '13700009004', '101 宿舍网络面板不通网', '网线插上指示灯不亮，同宿舍其余三个口正常，已换线排查确认是面板故障。', 2, NULL, 8, '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 101', 4, 2, 4, 'PENDING', 4, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(9003, 'DM260003', 9004, '13700009004', '201 洗手台水龙头漏水', '水龙头关到底仍在滴水，台面长期积水，已用盆临时接水。', 1, NULL, 10, '主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 201', 4, 2, 4, 'ASSIGNED', 2, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9004, 'DM260004', 9004, '13700009004', '102 宿舍吸顶灯闪烁', '灯管亮一段时间后开始闪烁，夜间影响休息。', 5, NULL, 9, '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 102', 4, 1, 1, 'ACCEPTED', 2, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9005, 'DM260005', 9004, '13700009004', '201 卫生间下水道返味', '地漏返味明显，已冲洗无效，怀疑存水弯干涸或密封圈老化。', 8, NULL, 10, '主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 201', 4, 1, 1, 'PROCESSING', 2, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9006, 'DM260006', 9004, '13700009004', '202 宿舍花洒出水很小', '花洒出水只有一半孔出水，拆下滤网清洗后仍无明显改善。', 1, NULL, 11, '主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 202', 4, 1, 1, 'SUSPENDED', 2, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(9007, 'DM260007', 9004, '13700009004', '102 宿舍洗手池下水堵塞', '洗手池下水缓慢，积水十几分钟才排完。', 1, NULL, 9, '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 102', 4, 1, 1, 'PENDING_CONFIRM', 2, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(9008, 'DM260008', 9004, '13700009004', 'A101 教室窗帘轨道脱落', '轨道一端固定件松动，窗帘整幅掉下，已卷起放置。', 9, NULL, 17, '主校区 / 教学区 / 教学楼A / 1层 / A101', 15, 1, 1, 'COMPLETED', 5, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(9009, 'DM260009', 9004, '13700009004', '302 宿舍空调制冷不足', '开 26 度仍感觉不到凉意，出风口温度偏高。', 3, NULL, 14, '主校区 / 宿舍区 / 2号宿舍楼 / 3层 / 302', 5, 1, 1, 'COMPLETED', 3, DATE_SUB(NOW(), INTERVAL 21 DAY)),
(9010, 'DM260010', 9004, '13700009004', '101 宿舍门被反锁', '出门倒垃圾门被风带上，钥匙在屋内。', 4, NULL, 8, '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 101', 4, 3, 7, 'REJECTED', 5, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9011, 'DM260011', 9004, '13700009004', '教学楼A 走廊灯罩破损', '三层的灯罩缺角，担心掉落。', 7, NULL, 15, '主校区 / 教学区 / 教学楼A', 15, 1, 1, 'CANCELLED', 5, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9012, 'DM260012', 9004, '13700009004', '302 宿舍空调再次不制冷（质保）', '上次维修后不到一周又出现同样问题，出风不凉。', 3, NULL, 14, '主校区 / 宿舍区 / 2号宿舍楼 / 3层 / 302', 5, 2, 4, 'PENDING', 3, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(9013, 'DM260013', 9004, '13700009004', '202 宿舍水龙头接口渗水（返修）', '上次更换软管后接口仍有渗水痕迹，地面潮湿。', 1, NULL, 11, '主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 202', 4, 2, 4, 'PENDING', 2, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9014, 'DM260014', 9004, '13700009004', '101 宿舍网速极慢', '白天网页都打不开，测速不到 1Mbps。', 2, NULL, 8, '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 101', 4, 1, 1, 'COMPLETED', 4, DATE_SUB(NOW(), INTERVAL 86 DAY)),
(9015, 'DM260015', 9004, '13700009004', '201 宿舍热水器不出热水', '打开热水档只有冷水，指示灯正常。', 1, NULL, 10, '主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 201', 4, 2, 4, 'COMPLETED', 2, DATE_SUB(NOW(), INTERVAL 70 DAY)),
(9016, 'DM260016', 9004, '13700009004', 'A201 教室日光灯不亮', '靠窗一排三盏灯全不亮。', 5, NULL, 19, '主校区 / 教学区 / 教学楼A / 2层 / A201', 15, 1, 1, 'COMPLETED', 2, DATE_SUB(NOW(), INTERVAL 45 DAY)),
(9017, 'DM260017', 9004, '13700009004', '102 宿舍水表箱异响', '夜间水表箱内有持续嗡鸣声。', 1, NULL, 9, '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 102', 4, 1, 1, 'COMPLETED', 2, DATE_SUB(NOW(), INTERVAL 25 DAY)),
(9018, 'DM260018', 9002, '13800009002', '后勤办公室门牌松动', '门牌一侧螺丝脱落，悬在门框上。', 9, NULL, 17, '主校区 / 教学区 / 教学楼A / 1层 / A101', 15, 1, 1, 'COMPLETED', 5, DATE_SUB(NOW(), INTERVAL 12 DAY)),
(9019, 'DM260019', 9004, '13700009004', '301 宿舍网线接口松动', '网线插上稍微一动就断，接口有旷量。', 2, NULL, 13, '主校区 / 宿舍区 / 2号宿舍楼 / 3层 / 301', 5, 1, 1, 'PENDING_CONFIRM', 4, DATE_SUB(NOW(), INTERVAL 11 DAY)),
(9020, 'DM260020', 9004, '13700009004', 'A201 教室空调漏水（需改约）', '空调内机下方滴水，已用桶接住。', 3, NULL, 19, '主校区 / 教学区 / 教学楼A / 2层 / A201', 15, 2, 4, 'PROCESSING', 3, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ---- 生命周期字段补齐（按状态） ----

-- 9001 待审核：无审核信息
-- 9002 待处理（抢单池）：已审核通过 2 小时，24h 自动派单尚未触发
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 2 HOUR) WHERE id = 9002;

-- 9003 已分配：指派给演示维修工，等其接单（可演示接单 / 退单）
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 23 HOUR), assignee_id = 9003
WHERE id = 9003;

-- 9004 已接单：维修工给出 3 个可上门时段，等学生选择
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 2 DAY),
  assignee_id = 9003, accepted_at = DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE id = 9004;

-- 9005 处理中：预约已确认，已到达并开始维修
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 3 DAY),
  assignee_id = 9003, accepted_at = DATE_SUB(NOW(), INTERVAL 3 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 1 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE id = 9005;

-- 9006 挂起：缺配件，等待采买
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 4 DAY),
  assignee_id = 9003, accepted_at = DATE_SUB(NOW(), INTERVAL 4 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 3 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 3 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 3 DAY), suspend_start_at = DATE_SUB(NOW(), INTERVAL 2 DAY)
WHERE id = 9006;

-- 9007 待确认：维修完成，验收截止设为 20 天后（避免定时任务自动验收，保证演示态稳定）
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 5 DAY),
  assignee_id = 9003, accepted_at = DATE_SUB(NOW(), INTERVAL 5 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 2 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 2 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 2 DAY), finished_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
  confirm_deadline = DATE_ADD(NOW(), INTERVAL 20 DAY)
WHERE id = 9007;

-- 9008 已完成（未评价，可演示评价）；同时作为一条「待评价」入口
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 9 DAY),
  assignee_id = 9003, accepted_at = DATE_SUB(NOW(), INTERVAL 8 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 7 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 7 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 7 DAY), finished_at = DATE_SUB(NOW(), INTERVAL 7 DAY),
  confirmed_at = DATE_SUB(NOW(), INTERVAL 6 DAY)
WHERE id = 9008;

-- 9009 已完成（已评价、可作质保父单）
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 21 DAY),
  assignee_id = 9003, accepted_at = DATE_SUB(NOW(), INTERVAL 20 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 19 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 19 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 19 DAY), finished_at = DATE_SUB(NOW(), INTERVAL 19 DAY),
  confirmed_at = DATE_SUB(NOW(), INTERVAL 18 DAY)
WHERE id = 9009;

-- 9010 已驳回：非设施故障，属使用不当
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 2 DAY),
  reject_reason = '上门核实为同学忘带钥匙被反锁，属个人事务，不属设施报修范围；已电话告知可联系宿管开锁。'
WHERE id = 9010;

-- 9011 已取消：由后勤管理员取消（重复报修）
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 2 DAY),
  cancelled_at = DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE id = 9011;

-- 9012 质保返修：跳过审核直接进抢单池，关联原单 9009，优先级 +1
UPDATE repair_order SET is_warranty = 1, parent_order_id = 9009, review_at = DATE_SUB(NOW(), INTERVAL 1 HOUR)
WHERE id = 9012;

-- 9013 返修流程：曾被退回返修，rework_flag=1、次数 1，重新回到抢单池
UPDATE repair_order SET rework_flag = 1, rework_count = 1, review_at = DATE_SUB(NOW(), INTERVAL 3 HOUR)
WHERE id = 9013;

-- 9014 ~ 9018 历史已完成工单（用于近 6 个月趋势与绩效统计）
UPDATE repair_order SET review_by = 9002, assignee_id = 9003,
  review_at = DATE_SUB(NOW(), INTERVAL 88 DAY), accepted_at = DATE_SUB(NOW(), INTERVAL 87 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 86 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 86 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 86 DAY), finished_at = DATE_SUB(NOW(), INTERVAL 86 DAY),
  confirmed_at = DATE_SUB(NOW(), INTERVAL 85 DAY), auto_confirmed = 1
WHERE id = 9014;

UPDATE repair_order SET review_by = 9002, assignee_id = 9003,
  review_at = DATE_SUB(NOW(), INTERVAL 72 DAY), accepted_at = DATE_SUB(NOW(), INTERVAL 71 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 70 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 70 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 70 DAY), finished_at = DATE_SUB(NOW(), INTERVAL 70 DAY),
  confirmed_at = DATE_SUB(NOW(), INTERVAL 69 DAY)
WHERE id = 9015;

UPDATE repair_order SET review_by = 9002, assignee_id = 9003,
  review_at = DATE_SUB(NOW(), INTERVAL 47 DAY), accepted_at = DATE_SUB(NOW(), INTERVAL 46 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 45 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 45 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 45 DAY), finished_at = DATE_SUB(NOW(), INTERVAL 45 DAY),
  confirmed_at = DATE_SUB(NOW(), INTERVAL 44 DAY)
WHERE id = 9016;

UPDATE repair_order SET review_by = 9002, assignee_id = 9003,
  review_at = DATE_SUB(NOW(), INTERVAL 27 DAY), accepted_at = DATE_SUB(NOW(), INTERVAL 26 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 25 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 25 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 25 DAY), finished_at = DATE_SUB(NOW(), INTERVAL 25 DAY),
  confirmed_at = DATE_SUB(NOW(), INTERVAL 24 DAY)
WHERE id = 9017;

UPDATE repair_order SET review_by = 9002, assignee_id = 9003,
  review_at = DATE_SUB(NOW(), INTERVAL 14 DAY), accepted_at = DATE_SUB(NOW(), INTERVAL 13 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 12 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 12 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 12 DAY), finished_at = DATE_SUB(NOW(), INTERVAL 12 DAY),
  confirmed_at = DATE_SUB(NOW(), INTERVAL 11 DAY), auto_confirmed = 1
WHERE id = 9018;

-- 9019 待确认（已批准延期，验收截止顺延）
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 11 DAY),
  assignee_id = 9003, accepted_at = DATE_SUB(NOW(), INTERVAL 11 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 10 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 10 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 10 DAY), finished_at = DATE_SUB(NOW(), INTERVAL 10 DAY),
  confirm_deadline = DATE_ADD(NOW(), INTERVAL 15 DAY)
WHERE id = 9019;

-- 9020 处理中（维修工发起改约，等学生确认新时段）
UPDATE repair_order SET review_by = 9002, review_at = DATE_SUB(NOW(), INTERVAL 2 DAY),
  assignee_id = 9003, accepted_at = DATE_SUB(NOW(), INTERVAL 2 DAY),
  plan_time = DATE_SUB(NOW(), INTERVAL 1 DAY), arrived_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
  started_at = DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE id = 9020;

-- ------------------------------------------------------------
-- 3. 状态流转日志（时间轴数据源）
-- ------------------------------------------------------------
INSERT INTO repair_order_log (order_id, from_status, to_status, action, operator_id, operator_role, remark, created_at) VALUES
(9001, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 3 HOUR)),

(9002, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(9002, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '情况属实，转派单池', DATE_SUB(NOW(), INTERVAL 2 HOUR)),

(9003, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 26 HOUR)),
(9003, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '同意维修', DATE_SUB(NOW(), INTERVAL 23 HOUR)),
(9003, 'PENDING', 'ASSIGNED', 'ASSIGN', 9002, 'MANAGER', '指派给水电组张建国', DATE_SUB(NOW(), INTERVAL 23 HOUR)),

(9004, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9004, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '同意维修', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9004, 'PENDING', 'ACCEPTED', 'GRAB', 9003, 'REPAIRER', '抢单成功', DATE_SUB(NOW(), INTERVAL 1 DAY)),

(9005, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9005, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '同意维修', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9005, 'PENDING', 'ACCEPTED', 'GRAB', 9003, 'REPAIRER', '抢单成功', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9005, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 9003, 'REPAIRER', '已确认上门时间', DATE_SUB(NOW(), INTERVAL 1 DAY)),

(9006, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(9006, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '同意维修', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(9006, 'PENDING', 'ACCEPTED', 'GRAB', 9003, 'REPAIRER', '抢单成功', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(9006, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 9003, 'REPAIRER', '已确认上门时间', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9006, 'PROCESSING', 'SUSPENDED', 'SUSPEND', 9003, 'REPAIRER', '花洒为特殊型号，库房无配件，需采买后继续（预计 3 天）', DATE_SUB(NOW(), INTERVAL 2 DAY)),

(9007, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(9007, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '同意维修', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(9007, 'PENDING', 'ACCEPTED', 'GRAB', 9003, 'REPAIRER', '抢单成功', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(9007, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 9003, 'REPAIRER', '已确认上门时间', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9007, 'PROCESSING', 'PENDING_CONFIRM', 'SUBMIT_RESULT', 9003, 'REPAIRER', '已疏通下水并更换密封圈，请验收', DATE_SUB(NOW(), INTERVAL 1 DAY)),

(9008, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(9008, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '同意维修', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(9008, 'PENDING', 'ACCEPTED', 'GRAB', 9003, 'REPAIRER', '抢单成功', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(9008, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 9003, 'REPAIRER', '已确认上门时间', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(9008, 'PROCESSING', 'PENDING_CONFIRM', 'SUBMIT_RESULT', 9003, 'REPAIRER', '已重新固定轨道并加装膨胀螺栓', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(9008, 'PENDING_CONFIRM', 'COMPLETED', 'CONFIRM', 9004, 'STUDENT', '已确认完成', DATE_SUB(NOW(), INTERVAL 6 DAY)),

(9009, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 21 DAY)),
(9009, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '同意维修', DATE_SUB(NOW(), INTERVAL 21 DAY)),
(9009, 'PENDING', 'ACCEPTED', 'GRAB', 9003, 'REPAIRER', '抢单成功', DATE_SUB(NOW(), INTERVAL 20 DAY)),
(9009, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 9003, 'REPAIRER', '已确认上门时间', DATE_SUB(NOW(), INTERVAL 19 DAY)),
(9009, 'PROCESSING', 'PENDING_CONFIRM', 'SUBMIT_RESULT', 9003, 'REPAIRER', '已补加制冷剂并保压测试，请验收', DATE_SUB(NOW(), INTERVAL 19 DAY)),
(9009, 'PENDING_CONFIRM', 'COMPLETED', 'CONFIRM', 9004, 'STUDENT', '已确认完成', DATE_SUB(NOW(), INTERVAL 18 DAY)),

(9010, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9010, 'PENDING_REVIEW', 'REJECTED', 'REVIEW_REJECT', 9002, 'MANAGER', '属个人事务，非设施报修范围', DATE_SUB(NOW(), INTERVAL 2 DAY)),

(9011, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9011, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '同意维修', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9011, 'PENDING', 'CANCELLED', 'CANCEL', 9002, 'MANAGER', '与 9008 窗帘轨道报修重复，合并处理', DATE_SUB(NOW(), INTERVAL 1 DAY)),

(9012, NULL, 'PENDING', 'CREATE', 9004, 'STUDENT', '质保返修：关联原单 DM260009，跳过审核直接进入抢单池', DATE_SUB(NOW(), INTERVAL 1 HOUR)),

(9013, NULL, 'PENDING', 'CREATE', 9004, 'STUDENT', '返修退回：原维修完成后接口仍渗水，退回重新派单', DATE_SUB(NOW(), INTERVAL 3 HOUR)),

(9019, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 11 DAY)),
(9019, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 9002, 'MANAGER', '同意维修', DATE_SUB(NOW(), INTERVAL 11 DAY)),
(9019, 'PENDING', 'ACCEPTED', 'GRAB', 9003, 'REPAIRER', '抢单成功', DATE_SUB(NOW(), INTERVAL 11 DAY)),
(9019, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 9003, 'REPAIRER', '已确认上门时间', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(9019, 'PROCESSING', 'PENDING_CONFIRM', 'SUBMIT_RESULT', 9003, 'REPAIRER', '已更换面板卡扣，请验收', DATE_SUB(NOW(), INTERVAL 10 DAY)),

(9020, NULL, 'PENDING_REVIEW', 'CREATE', 9004, 'STUDENT', '提交报修', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9020, 'PENDING', 'ACCEPTED', 'GRAB', 9003, 'REPAIRER', '抢单成功', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9020, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 9003, 'REPAIRER', '已确认上门时间', DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ------------------------------------------------------------
-- 4. 预约时段
-- ------------------------------------------------------------
INSERT INTO repair_schedule (order_id, repairer_id, plan_date, slot_start, slot_end, status, proposed_by, remark) VALUES
-- 9004：3 个待选择时段
(9004, 9003, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00:00', '11:00:00', 'OFFERED', 'REPAIRER', NULL),
(9004, 9003, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00', '16:00:00', 'OFFERED', 'REPAIRER', NULL),
(9004, 9003, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '09:00:00', '11:00:00', 'OFFERED', 'REPAIRER', NULL),
-- 9005：已确认并完成上门
(9005, 9003, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:00:00', '11:00:00', 'CONFIRMED', 'REPAIRER', '已按约定上门'),
-- 9006：已确认（工单随后挂起）
(9006, 9003, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '14:00:00', '16:00:00', 'CONFIRMED', 'REPAIRER', NULL),
-- 9007：已确认
(9007, 9003, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '09:00:00', '11:00:00', 'CONFIRMED', 'REPAIRER', NULL),
-- 9020：原时段已确认 + 维修工新增改约时段待学生选择
(9020, 9003, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '14:00:00', '16:00:00', 'CONFIRMED', 'REPAIRER', NULL),
(9020, 9003, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00', '17:00:00', 'OFFERED', 'REPAIRER', '需带加长排水管，改为明天下午上门'),
-- 历史单：已确认并完成
(9008, 9003, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '10:00:00', '12:00:00', 'CONFIRMED', 'REPAIRER', NULL),
(9009, 9003, DATE_SUB(CURDATE(), INTERVAL 19 DAY), '15:00:00', '17:00:00', 'CONFIRMED', 'REPAIRER', NULL),
(9014, 9003, DATE_SUB(CURDATE(), INTERVAL 86 DAY), '09:00:00', '11:00:00', 'CONFIRMED', 'REPAIRER', NULL),
(9015, 9003, DATE_SUB(CURDATE(), INTERVAL 70 DAY), '14:00:00', '16:00:00', 'CONFIRMED', 'REPAIRER', NULL),
(9016, 9003, DATE_SUB(CURDATE(), INTERVAL 45 DAY), '09:00:00', '10:00:00', 'CONFIRMED', 'REPAIRER', NULL),
(9017, 9003, DATE_SUB(CURDATE(), INTERVAL 25 DAY), '16:00:00', '18:00:00', 'CONFIRMED', 'REPAIRER', NULL),
(9018, 9003, DATE_SUB(CURDATE(), INTERVAL 12 DAY), '10:00:00', '11:00:00', 'CONFIRMED', 'REPAIRER', NULL),
(9019, 9003, DATE_SUB(CURDATE(), INTERVAL 10 DAY), '09:00:00', '10:00:00', 'CONFIRMED', 'REPAIRER', NULL);

-- ------------------------------------------------------------
-- 5. 维修记录（到达 / 开始 / 结束打点）
-- ------------------------------------------------------------
INSERT INTO repair_record (order_id, repairer_id, arrive_at, start_at, end_at, content, material, result) VALUES
(9005, 9003, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL,
 '拆开地漏检查，存水弯内积存毛发与皂垢，已清理并对密封圈做临时处理，待观察返味是否消除。',
 '疏通弹簧、手套', NULL),
(9006, 9003, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL,
 '花洒为老式四档型号，库房无同型号配件；已登记配件需求，等待采买到货后更换。',
 '除垢剂', NULL),
(9007, 9003, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
 '拆下洗手池下水管，清除管内毛发与油垢，更换老化密封圈并重新打胶。',
 'PVC 下水管 1 根、密封圈 2 个、玻璃胶 1 支', '排水恢复正常，无渗漏'),
(9008, 9003, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
 '轨道固定件螺丝孔已滑丝，改用膨胀螺栓重新固定两端，并加装中间支撑件。',
 '膨胀螺栓 4 套、支撑件 1 个', '轨道固定牢固，窗帘可正常开合'),
(9009, 9003, DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY),
 '清洗过滤网与蒸发器，检测制冷剂压力偏低，补加 R32 制冷剂并做保压测试。',
 'R32 制冷剂 300g', '出风温度恢复正常，保压 30 分钟无泄漏'),
(9014, 9003, DATE_SUB(NOW(), INTERVAL 86 DAY), DATE_SUB(NOW(), INTERVAL 86 DAY), DATE_SUB(NOW(), INTERVAL 86 DAY),
 '交换机端口协商异常，更换跳线并重置端口后速率恢复千兆。', '六类跳线 1 根', '速率恢复'),
(9015, 9003, DATE_SUB(NOW(), INTERVAL 70 DAY), DATE_SUB(NOW(), INTERVAL 70 DAY), DATE_SUB(NOW(), INTERVAL 70 DAY),
 '加热管接线端子烧蚀，更换端子并紧固。', '接线端子 2 个', '热水恢复正常'),
(9016, 9003, DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY),
 '镇流器损坏，更换为同规格 LED 灯管。', 'LED 灯管 3 支', '照明恢复'),
(9017, 9003, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY),
 '水表阀芯松动产生共振，紧固阀芯并加装减振垫。', '减振垫 2 片', '异响消除'),
(9018, 9003, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY),
 '补齐门牌固定螺丝并调整水平。', '自攻螺丝 2 颗', '门牌固定牢固'),
(9019, 9003, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY),
 '更换网络面板卡扣与模块，重新打线测试通断。', '网络模块 1 个', '接口稳固，链路正常');

-- ------------------------------------------------------------
-- 6. 评价（9009 匿名已评；9017 实名已评；其余已完成单保持未评价，供演示）
-- ------------------------------------------------------------
INSERT INTO evaluation (order_id, student_id, repairer_id, speed_score, attitude_score, effect_score, total_score, content, anonymous, created_at) VALUES
(9009, 9004, 9003, 4, 5, 4, 4.33, '师傅上门准时，补了制冷剂后确实凉快了，就是希望下次能提前说一声大概几点到。', 1, DATE_SUB(NOW(), INTERVAL 17 DAY)),
(9017, 9004, 9003, 5, 5, 5, 5.00, '晚上十点多还在响，师傅第二天一早就来处理了，很专业。', 0, DATE_SUB(NOW(), INTERVAL 23 DAY));

-- ------------------------------------------------------------
-- 7. 投诉（待受理 / 处理中 / 已成立 三种状态）
-- ------------------------------------------------------------
INSERT INTO complaint (order_id, student_id, repairer_id, type, content, status, handler_id, handle_result, handled_at, created_at) VALUES
(9008, 9004, 9003, 'QUALITY', '窗帘轨道修完第三天中间又开始下垂，希望重新检查一下固定方式。', 'PENDING', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(9009, 9004, 9003, 'SLOW', '报修后两天才有人联系，中间没有任何进度说明。', 'PROCESSING', 9002, '已联系维修组核实当日派单情况，等待班组反馈。', DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY)),
(9017, 9004, 9003, 'ATTITUDE', '沟通时语气比较急，希望注意服务用语。', 'RESOLVED', 9002, '经核实情况属实，已与维修人员沟通提醒，并按制度扣减信誉分。', DATE_SUB(NOW(), INTERVAL 22 DAY), DATE_SUB(NOW(), INTERVAL 23 DAY));

-- ------------------------------------------------------------
-- 8. 延期确认申请（待审批 / 已通过）
-- ------------------------------------------------------------
INSERT INTO order_extension_request (order_id, student_id, reason, status, handler_id, handle_result, handled_at, created_at) VALUES
(9007, 9004, '本人在外地参加实习，两周内无法回校当面验收，申请延期确认。', 'PENDING', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(9019, 9004, '近期在准备竞赛，白天不在宿舍，申请延后一周确认。', 'APPROVED', 9002, '情况合理，同意延期 7 天。', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY));

-- ------------------------------------------------------------
-- 9. 工单聊天（学生 ↔ 维修工，含未读）
-- ------------------------------------------------------------
INSERT INTO chat_message (order_id, sender_id, receiver_id, content, read_flag, created_at) VALUES
(9005, 9004, 9003, '师傅您好，今天下午方便过来看下地漏吗？宿舍一直有人。', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9005, 9003, 9004, '可以，我大概 15:20 到，麻烦留一下门。', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9005, 9004, 9003, '好的，我在 201 等您。', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9005, 9003, 9004, '已经清理完存水弯，今晚先观察一下，如果还有味道我再过来换密封圈。', 0, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(9007, 9004, 9003, '师傅，下水已经通了，谢谢！不过台面下面还有一点点水渍。', 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9007, 9003, 9004, '那是拆管时残留的水，擦干就行，如果明天还在渗我再来看。', 1, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ------------------------------------------------------------
-- 10. 站内通知（覆盖工单 / 聊天 / 投诉 / 系统四类，含未读）
-- ------------------------------------------------------------
INSERT INTO notification (receiver_id, type, title, content, biz_id, read_flag, created_at) VALUES
-- 学生
(9004, 'ORDER_STATUS', '报修已通过审核', '您报修的「101 宿舍网络面板不通网」已通过审核，正在等待维修人员接单。', 9002, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(9004, 'ORDER_STATUS', '维修完成，待确认', '「102 宿舍洗手池下水堵塞」已维修完成，请在 3 天内确认，逾期将自动验收。', 9007, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9004, 'ORDER_STATUS', '可以评价了', '「A101 教室窗帘轨道脱落」已确认完成，欢迎对本次维修服务作出评价。', 9008, 0, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(9004, 'COMPLAINT', '投诉已受理', '您对「A101 教室窗帘轨道脱落」的投诉已由后勤管理员受理，正在核实。', 9008, 1, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(9004, 'CHAT', '维修人员发来消息', '张建国：已经清理完存水弯，今晚先观察一下。', 9005, 0, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(9004, 'SYSTEM', '账号安全提示', '为保障账号安全，请勿将密码告知他人；如发现异常登录请联系后勤管理员。', NULL, 1, DATE_SUB(NOW(), INTERVAL 30 DAY)),
-- 维修工
(9003, 'ORDER_STATUS', '新工单指派给您', '工单「201 洗手台水龙头漏水」已指派给您，请及时确认接单。', 9003, 0, DATE_SUB(NOW(), INTERVAL 23 HOUR)),
(9003, 'ORDER_STATUS', '报修人申请改约', '「A201 教室空调漏水」报修人尚未确认新时段，请关注处理进度。', 9020, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9003, 'COMPLAINT', '投诉成立通知', '关于「102 宿舍水表箱异响」的投诉经核实成立，已按制度扣减信誉分 10 分。', 9017, 1, DATE_SUB(NOW(), INTERVAL 22 DAY)),
(9003, 'CHAT', '报修人发来消息', '陈晓：师傅您好，今天下午方便过来看下地漏吗？', 9005, 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9003, 'SYSTEM', '配件到货提醒', '您登记的花洒配件已到库房，请及时领取并恢复「202 宿舍花洒出水很小」的处理。', 9006, 0, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
-- 后勤管理员
(9002, 'ORDER_STATUS', '有新的待审核工单', '「301 宿舍空调开机跳闸」等待审核，请及时处理。', 9001, 0, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9002, 'COMPLAINT', '收到新的投诉', '报修人对「A101 教室窗帘轨道脱落」的维修质量提出投诉，请受理。', 9008, 0, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(9002, 'ORDER_STATUS', '报修人申请延期确认', '「102 宿舍洗手池下水堵塞」报修人申请延期验收，请审批。', 9007, 0, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(9002, 'SYSTEM', '信息更改待审核', '维修人员张建国提交了手机号更改申请，请审核。', NULL, 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
-- 系统管理员
(9001, 'SYSTEM', '每日运行摘要', '昨日新增工单 3 张，完成 2 张，无超时未处理工单。', NULL, 0, DATE_SUB(NOW(), INTERVAL 10 HOUR)),
(9001, 'SYSTEM', '高频故障预警', '2号宿舍楼近 7 天出现 6 单「空调故障」，建议检查楼层配电与公共设备。', NULL, 0, DATE_SUB(NOW(), INTERVAL 9 HOUR));

-- ------------------------------------------------------------
-- 11. 信誉分流水（与账号当前分值一致）
-- ------------------------------------------------------------
INSERT INTO sys_credit_log (user_id, delta, before_score, after_score, reason, biz_id, operator_id, created_at) VALUES
(9004, -10, 100, 90, '超时未确认，系统自动验收', 9019, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(9004,   1,  90, 91, '按时完成并评价', 9009, NULL, DATE_SUB(NOW(), INTERVAL 17 DAY)),
(9003, -10, 100, 90, '投诉成立：服务态度', 9017, 9002, DATE_SUB(NOW(), INTERVAL 22 DAY));

-- ------------------------------------------------------------
-- 12. 信息更改申请（待审核 / 已通过）
-- ------------------------------------------------------------
INSERT INTO profile_change_request (applicant_id, field_key, field_label, old_value, new_value, reason, status, reviewer_id, reject_reason, reviewed_at, created_at) VALUES
(9003, 'phone', '手机号', '13900009003', '13900009013', '原号码停用，已办理新号，便于报修人联系。', 'PENDING', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9003, 'real_name', '姓名', '张建国', '张建国', '系统中姓名有误（少一个字），申请更正。', 'APPROVED', 9002, NULL, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 41 DAY));

-- ------------------------------------------------------------
-- 13. 高频故障预警（管理端横幅）
-- ------------------------------------------------------------
INSERT INTO fault_alert (id, building_id, category_id, order_count, window_start, window_end, status, created_at) VALUES
(9001, 5, 3, 6, DATE_SUB(CURDATE(), INTERVAL 7 DAY), CURDATE(), 'NEW', DATE_SUB(NOW(), INTERVAL 9 HOUR));

-- ------------------------------------------------------------
-- 14. 操作日志（覆盖成功 / 失败、多个模块）
-- ------------------------------------------------------------
INSERT INTO sys_oper_log (user_id, username, module, operation, method, request_uri, request_param, ip, status, error_msg, cost_ms, created_at) VALUES
(9002, 'demo_mgr', '工单管理', '审核通过', 'PUT', '/api/v1/orders/9002/review', '{"orderId":9002,"remark":"情况属实，转派单池"}', '192.168.10.24', 1, NULL, 42, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(9002, 'demo_mgr', '工单管理', '审核驳回', 'PUT', '/api/v1/orders/9010/reject', '{"orderId":9010,"reason":"属个人事务，非设施报修范围"}', '192.168.10.24', 1, NULL, 37, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9002, 'demo_mgr', '投诉管理', '处理投诉', 'PUT', '/api/v1/complaints/3/handle', '{"status":"RESOLVED","result":"经核实情况属实，已提醒并按制度扣分"}', '192.168.10.24', 1, NULL, 55, DATE_SUB(NOW(), INTERVAL 22 DAY)),
(9002, 'demo_mgr', '验收管理', '审批延期', 'PUT', '/api/v1/orders/9019/extension/approve', '{"approve":true,"remark":"同意延期 7 天"}', '192.168.10.24', 1, NULL, 31, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(9001, 'demo_admin', '用户管理', '新增用户', 'POST', '/api/v1/users', '{"username":"2026099","realName":"测试同学","role":"STUDENT"}', '192.168.10.11', 1, NULL, 128, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9001, 'demo_admin', '用户管理', '新增用户', 'POST', '/api/v1/users', '{"username":"2026099","realName":"测试同学","role":"STUDENT"}', '192.168.10.11', 0, '账号已存在', 96, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9001, 'demo_admin', '系统参数', '修改参数', 'PUT', '/api/v1/configs/order.auto-assign-hours', '{"value":"24"}', '192.168.10.11', 1, NULL, 22, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9001, 'demo_admin', '角色权限', '分配权限', 'PUT', '/api/v1/roles/3/permissions', '{"permissionIds":[10,11,12,15,20,21,30,31,40,60,62]}', '192.168.10.11', 1, NULL, 64, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(9001, 'demo_admin', '学生导入', '批量导入', 'POST', '/api/v1/users/import', '{"fileName":"2026级新生名单.xlsx","total":128}', '192.168.10.11', 1, NULL, 3120, DATE_SUB(NOW(), INTERVAL 7 DAY));

-- ------------------------------------------------------------
-- 15. 登录日志
-- ------------------------------------------------------------
INSERT INTO sys_login_log (user_id, username, ip, user_agent, status, msg, created_at) VALUES
(9004, 'demo_stu',   '192.168.20.101', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0', 1, '登录成功', DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(9003, 'demo_rep',   '192.168.20.55',  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0', 1, '登录成功', DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(9002, 'demo_mgr',   '192.168.10.24',  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/131.0',   1, '登录成功', DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9001, 'demo_admin', '192.168.10.11',  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/131.0',   1, '登录成功', DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(9004, 'demo_stu',   '192.168.20.101', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0', 0, '账号或密码错误', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9003, 'demo_rep',   '192.168.20.55',  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0', 1, '登录成功', DATE_SUB(NOW(), INTERVAL 3 DAY));
