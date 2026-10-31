-- ============================================================
-- CampusFix 测试数据  v1.0
-- 对应《CampusFix-项目注意文档》§4.4「测试数据要求」
-- 执行：mysql -uroot -p < db/data.sql
-- 所有账号初始密码：Cf@123456（BCrypt）
-- ============================================================

USE campusfix;
SET NAMES utf8mb4;

DELETE FROM repair_order_log;
DELETE FROM repair_order_image;
DELETE FROM repair_schedule;
DELETE FROM repair_record;
DELETE FROM evaluation;
DELETE FROM complaint;
DELETE FROM chat_message;
DELETE FROM notification;
DELETE FROM order_extension_request;
DELETE FROM fault_alert;
DELETE FROM repair_order;
DELETE FROM facility;
DELETE FROM facility_type;
DELETE FROM location;
DELETE FROM repair_category;
DELETE FROM sys_credit_log;
DELETE FROM sys_oper_log;
DELETE FROM sys_login_log;
DELETE FROM sys_config;
DELETE FROM sys_role_permission;
DELETE FROM sys_user_role;
DELETE FROM sys_permission;
DELETE FROM sys_role;
DELETE FROM sys_user;
DELETE FROM sys_dept;

-- ------------------------------------------------------------
-- 部门（多部门分片）
-- ------------------------------------------------------------
INSERT INTO sys_dept (id, parent_id, name, code, leader, phone, type, sort, status) VALUES
(1, 0, '后勤保障处', 'HQ',      '王主任', '0571-88000001', 'ADMIN',  1, 1),
(2, 1, '水电维修组', 'HQ-PL',   '张建国', '0571-88000002', 'REPAIR', 1, 1),
(3, 1, '空调维修组', 'HQ-AC',   '李伟',   '0571-88000003', 'REPAIR', 2, 1),
(4, 1, '网络维修组', 'HQ-NW',   '王强',   '0571-88000004', 'REPAIR', 3, 1),
(5, 1, '综合维修组', 'HQ-GN',   '刘敏',   '0571-88000005', 'REPAIR', 4, 1);

-- ------------------------------------------------------------
-- 角色
-- ------------------------------------------------------------
INSERT INTO sys_role (id, code, name, remark, status) VALUES
(1, 'STUDENT',  '学生',      '提交报修、确认、评价', 1),
(2, 'REPAIRER', '维修人员',  '抢单、维修、提交结果', 1),
(3, 'MANAGER',  '后勤管理员', '审核、指派、统计',     1),
(4, 'ADMIN',    '系统管理员', '系统级管理',           1);

-- ------------------------------------------------------------
-- 权限（菜单 + 按钮）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, code, name, type, path, sort) VALUES
(10,  0, 'order:list',       '查看工单',     'API',    '/orders',              10),
(11,  0, 'order:review',     '审核工单',     'BUTTON', NULL,                   11),
(12,  0, 'order:assign',     '指派/转派',    'BUTTON', NULL,                   12),
(13,  0, 'order:grab',       '抢单池',       'BUTTON', '/orders/pool',         13),
(14,  0, 'order:handle',     '处理工单',     'BUTTON', NULL,                   14),
(15,  0, 'order:cancel',     '取消工单',     'BUTTON', NULL,                   15),
(16,  0, 'order:list:all',   '查看全部工单', 'API',    NULL,                   16),
(20,  0, 'complaint:list',   '投诉列表',     'API',    '/complaints',          20),
(21,  0, 'complaint:handle', '处理投诉',     'BUTTON', NULL,                   21),
(30,  0, 'facility:edit',    '设施维护',     'BUTTON', '/facilities',          30),
(31,  0, 'category:edit',    '故障类型维护', 'BUTTON', '/categories',          31),
(40,  0, 'stats:view',       '数据统计',     'API',    '/stats',               40),
(50,  0, 'user:list',        '用户管理',     'API',    '/users',               50),
(51,  0, 'user:add',         '新增用户',     'BUTTON', NULL,                   51),
(52,  0, 'user:edit',        '编辑用户',     'BUTTON', NULL,                   52),
(53,  0, 'user:import',      '批量导入',     'BUTTON', NULL,                   53),
(54,  0, 'role:list',        '角色管理',     'API',    '/roles',               54),
(55,  0, 'role:edit',        '编辑角色',     'BUTTON', NULL,                   55),
(56,  0, 'role:auth',        '分配权限',     'BUTTON', NULL,                   56),
(57,  0, 'dept:edit',        '部门维护',     'BUTTON', '/depts',               57),
(58,  0, 'config:list',      '系统参数',     'API',    '/configs',             58),
(59,  0, 'config:edit',      '修改参数',     'BUTTON', NULL,                   59),
(60,  0, 'log:list',         '日志查看',     'API',    '/logs',                60);

-- ADMIN 全量
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 4, id FROM sys_permission;

-- MANAGER 部门级
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 10), (3, 11), (3, 12), (3, 15), (3, 20), (3, 21),
(3, 30), (3, 31), (3, 40), (3, 60);

-- REPAIRER
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 10), (2, 13), (2, 14);

-- STUDENT：仅能查看自己的工单（数据权限由拦截器控制）
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 10);

-- ------------------------------------------------------------
-- 用户（学号 / 工号注入，无需注册）
-- 初始密码 Cf@123456
-- ------------------------------------------------------------
SET @PWD = '$2b$10$H36cH3AlKv..rd9XSydhqOOphTw.ZADBBbA5M0/NzkPYG64iMlvd6';

INSERT INTO sys_user (id, username, password, real_name, student_no, staff_no, dept_id, phone, credit_score, status, pwd_reset_flag) VALUES
(1,  '1000',    @PWD, '系统管理员', NULL,      '1000',  1,    '13800000001', 100, 1, 0),
(2,  '20001',   @PWD, '王海涛',     NULL,      '20001', 1,    '13800000002', 100, 1, 0),
(3,  '20002',   @PWD, '周文',       NULL,      '20002', 1,    '13800000003', 100, 1, 0),
(4,  '30001',   @PWD, '张建国',     NULL,      '30001', 2,    '13900000001', 100, 1, 0),
(5,  '30002',   @PWD, '李伟',       NULL,      '30002', 3,    '13900000002', 100, 1, 0),
(6,  '30003',   @PWD, '王强',       NULL,      '30003', 4,    '13900000003', 100, 1, 0),
(7,  '2021001', @PWD, '陈晓',       '2021001', NULL,    NULL, '13700000001', 100, 1, 0),
(8,  '2021002', @PWD, '刘洋',       '2021002', NULL,    NULL, '13700000002', 95,  1, 0),
(9,  '2021003', @PWD, '赵敏',       '2021003', NULL,    NULL, '13700000003', 100, 1, 0),
(10, '2021004', @PWD, '孙磊',       '2021004', NULL,    NULL, '13700000004', 100, 1, 0),
(11, '2021005', @PWD, '周婷',       '2021005', NULL,    NULL, '13700000005', 100, 1, 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 4), (2, 3), (3, 3), (4, 2), (5, 2), (6, 2),
(7, 1), (8, 1), (9, 1), (10, 1), (11, 1);

-- ------------------------------------------------------------
-- 系统参数
-- ------------------------------------------------------------
INSERT INTO sys_config (config_key, config_value, value_type, remark) VALUES
('credit.init-score',         '100',   'INT',     '初始信誉分'),
('credit.deduct-score',       '10',    'INT',     '单次扣分'),
('credit.limit-threshold',    '60',    'INT',     '限权阈值'),
('order.auto-assign-hours',   '24',    'INT',     '待处理超时自动派单（小时）'),
('order.auto-confirm-days',   '3',     'INT',     '待确认超时自动验收（天）'),
('order.max-images',          '9',     'INT',     '单工单图片上限'),
('order.warranty-days',       '3',     'INT',     '质保期（天）'),
('evaluation.speed-weight',   '0.34',  'DECIMAL', '评价权重：维修速度'),
('evaluation.attitude-weight','0.33',  'DECIMAL', '评价权重：服务态度'),
('evaluation.effect-weight',  '0.33',  'DECIMAL', '评价权重：维修效果');

-- ------------------------------------------------------------
-- 故障类型（9 类）
-- ------------------------------------------------------------
INSERT INTO repair_category (id, name, code, dept_id, sort, status) VALUES
(1, '水电故障', 'PLUMBING', 2, 1, 1),
(2, '网络故障', 'NETWORK',  4, 2, 1),
(3, '空调故障', 'AIRCON',   3, 3, 1),
(4, '门窗故障', 'DOOR',     5, 4, 1),
(5, '照明故障', 'LIGHT',    2, 5, 1),
(6, '家具损坏', 'FURNITURE',5, 6, 1),
(7, '公共设施', 'PUBLIC',   5, 7, 1),
(8, '卫生设施', 'SANITARY', 2, 8, 1),
(9, '其他',     'OTHER',    5, 9, 1);

-- ------------------------------------------------------------
-- 位置树：校区 → 区域 → 楼栋 → 楼层 → 房间
-- ------------------------------------------------------------
INSERT INTO location (id, parent_id, level, name, code, type, sort) VALUES
(1,  0, 1, '主校区',        'C01',                  'CAMPUS',   1),
(2,  1, 2, '宿舍区',        'C01-SS',               'AREA',     1),
(3,  1, 2, '教学区',        'C01-TS',               'AREA',     2),
(4,  2, 3, '1号宿舍楼',     'C01-SS-B01',           'BUILDING', 1),
(5,  2, 3, '2号宿舍楼',     'C01-SS-B02',           'BUILDING', 2),
(6,  4, 4, '1层',           'C01-SS-B01-F01',       'FLOOR',    1),
(7,  4, 4, '2层',           'C01-SS-B01-F02',       'FLOOR',    2),
(8,  6, 5, '101',           'C01-SS-B01-F01-R101',  'ROOM',     1),
(9,  6, 5, '102',           'C01-SS-B01-F01-R102',  'ROOM',     2),
(10, 7, 5, '201',           'C01-SS-B01-F02-R201',  'ROOM',     1),
(11, 7, 5, '202',           'C01-SS-B01-F02-R202',  'ROOM',     2),
(12, 5, 4, '3层',           'C01-SS-B02-F03',       'FLOOR',    1),
(13, 12,5, '301',           'C01-SS-B02-F03-R301',  'ROOM',     1),
(14, 12,5, '302',           'C01-SS-B02-F03-R302',  'ROOM',     2),
(15, 3, 3, '教学楼A',       'C01-TS-B01',           'BUILDING', 1),
(16, 15,4, '1层',           'C01-TS-B01-F01',       'FLOOR',    1),
(17, 16,5, 'A101',          'C01-TS-B01-F01-R101',  'ROOM',     1),
(18, 15,4, '2层',           'C01-TS-B01-F02',       'FLOOR',    2),
(19, 18,5, 'A201',          'C01-TS-B01-F02-R201',  'ROOM',     1);

-- ------------------------------------------------------------
-- 设施
-- ------------------------------------------------------------
INSERT INTO facility_type (id, name, code, sort) VALUES
(1, '空调', 'AC', 1), (2, '给排水', 'PL', 2), (3, '网络设备', 'NW', 3),
(4, '门窗', 'DR', 4), (5, '照明', 'LT', 5), (6, '家具', 'FN', 6);

INSERT INTO facility (id, facility_no, name, type_id, location_id, install_date, status, remark) VALUES
(1, 'AC-2024-00128', '2号楼301宿舍空调', 1, 13, '2024-09-01', 'REPAIRING', '格力 1.5P 挂机'),
(2, 'AC-2024-00129', '2号楼302宿舍空调', 1, 14, '2024-09-01', 'NORMAL',    '格力 1.5P 挂机'),
(3, 'PL-2024-00031', '2号楼301洗手台水管', 2, 13, '2024-09-01', 'NORMAL',   NULL),
(4, 'NW-2023-00007', '1号楼101网络面板', 3, 8,  '2023-09-01', 'NORMAL',    NULL),
(5, 'LT-2024-00201', '教学楼A101吸顶灯', 5, 17, '2024-09-01', 'NORMAL',    NULL);

-- ------------------------------------------------------------
-- 样例工单：覆盖全部 10 种状态
-- ------------------------------------------------------------
INSERT INTO repair_order
(id, order_no, reporter_id, reporter_phone, title, description, category_id, facility_id,
 location_id, location_path, building_id, urgency, priority, status, dept_id, assignee_id,
 review_by, review_at, accepted_at, arrived_at, started_at, finished_at, confirmed_at,
 confirm_deadline, plan_time, created_at) VALUES
(1, '20261030001', 7, '13700000001', '301宿舍空调不制冷', '开机后只出风不制冷，已持续两天，遥控器显示 E3 故障码。', 3, 1,
 13, '主校区 / 宿舍区 / 2号宿舍楼 / 3层 / 301', 5, 2, 6, 'PENDING_REVIEW', 3, NULL,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-10-30 09:12:00'),

(2, '20261030002', 8, '13700000002', '101宿舍网络无法连接', '墙上网口插线后指示灯不亮，手机连 Wi-Fi 也频繁掉线。', 2, 4,
 8, '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 101', 4, 1, 1, 'PENDING', 4, NULL,
 2, '2026-10-30 09:30:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-10-30 08:40:00'),

(3, '20261030003', 7, '13700000001', 'A201教室投影没信号', '投影仪显示无信号，换过 HDMI 线无效。', 2, NULL,
 19, '主校区 / 教学区 / 教学楼A / 2层 / A201', 15, 2, 5, 'ASSIGNED', 4, 6,
 2, '2026-10-30 10:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-10-30 09:50:00'),

(4, '20261030004', 9, '13700000003', '301宿舍走廊灯不亮', '走廊尽头两盏灯都不亮，晚上看不清。', 5, NULL,
 13, '主校区 / 宿舍区 / 2号宿舍楼 / 3层 / 301', 5, 1, 2, 'ACCEPTED', 2, 4,
 3, '2026-10-30 10:20:00', '2026-10-30 10:35:00', NULL, NULL, NULL, NULL, NULL, NULL, '2026-10-30 10:10:00'),

(5, '20261030005', 8, '13700000002', '302宿舍门锁卡住', '门锁钥匙能转但打不开，需要用力推。', 4, NULL,
 14, '主校区 / 宿舍区 / 2号宿舍楼 / 3层 / 302', 5, 2, 4, 'PROCESSING', 5, 5,
 2, '2026-10-29 15:00:00', '2026-10-29 15:20:00', '2026-10-30 09:00:00', '2026-10-30 09:10:00',
 NULL, NULL, NULL, '2026-10-30 09:10:00', '2026-10-29 14:40:00'),

(6, '20261030006', 10, '13700000004', '201宿舍空调漏水', '空调内机滴水，地面已经积水。', 3, NULL,
 10, '主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 201', 4, 3, 8, 'SUSPENDED', 3, 5,
 3, '2026-10-29 09:00:00', '2026-10-29 09:15:00', '2026-10-29 10:00:00', '2026-10-29 10:05:00',
 NULL, NULL, NULL, '2026-10-29 10:05:00', '2026-10-29 08:30:00'),

(7, '20261030007', 11, '13700000005', '301洗手台水管漏水', '洗手台下方接口渗水，关不紧。', 1, 3,
 13, '主校区 / 宿舍区 / 2号宿舍楼 / 3层 / 301', 5, 2, 3, 'PENDING_CONFIRM', 2, 4,
 2, '2026-10-28 14:00:00', '2026-10-28 14:20:00', '2026-10-29 09:00:00', '2026-10-29 09:05:00',
 '2026-10-29 10:20:00', NULL, '2026-11-01 10:20:00', '2026-10-29 09:05:00', '2026-10-28 13:40:00'),

(8, '20261030008', 7, '13700000001', '101宿舍网络面板损坏', '面板卡扣断裂，网线插不牢。', 2, 4,
 8, '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 101', 4, 1, 1, 'COMPLETED', 4, 6,
 2, '2026-10-26 09:00:00', '2026-10-26 09:20:00', '2026-10-27 09:00:00', '2026-10-27 09:10:00',
 '2026-10-27 09:45:00', '2026-10-28 10:00:00', NULL, '2026-10-27 09:00:00', '2026-10-26 08:45:00'),

(9, '20261030009', 8, '13700000002', '102宿舍要求更换整套家具', '希望把书桌、衣柜全部换成新的。', 6, NULL,
 9, '主校区 / 宿舍区 / 1号宿舍楼 / 1层 / 102', 4, 1, 1, 'REJECTED', 5, NULL,
 3, '2026-10-27 11:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-10-27 10:30:00'),

(10, '20261030010', 9, '13700000003', '202宿舍想加装插座', '想在床头加两个插座。', 1, NULL,
 11, '主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 202', 4, 1, 1, 'CANCELLED', 2, NULL,
 2, '2026-10-27 16:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-10-27 15:30:00');

INSERT INTO repair_order (id, order_no, reporter_id, reporter_phone, title, description, category_id, facility_id,
 location_id, location_path, building_id, urgency, priority, status, dept_id, assignee_id, reject_reason,
 review_by, review_at, cancelled_at, created_at) VALUES
(11, '20261030011', 10, '13700000004', '201宿舍天花板渗水', '天花板角落渗水，怀疑楼上管道问题。', 1, NULL,
 10, '主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 201', 4, 3, 7, 'PENDING', 2, NULL, NULL,
 3, '2026-10-28 08:30:00', NULL, '2026-10-28 08:05:00');

UPDATE repair_order SET reject_reason = '不属于维修范围：整套家具更换需走资产采购流程。' WHERE id = 9;
UPDATE repair_order SET reject_reason = '宿舍管理规定不允许私自加装插座' WHERE id = 10;

-- 状态流转日志
INSERT INTO repair_order_log (order_id, from_status, to_status, action, operator_id, operator_role, remark, created_at) VALUES
(1, NULL, 'PENDING_REVIEW', 'CREATE', 7, 'STUDENT', NULL, '2026-10-30 09:12:00'),
(2, NULL, 'PENDING_REVIEW', 'CREATE', 8, 'STUDENT', NULL, '2026-10-30 08:40:00'),
(2, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 2, 'MANAGER', '情况属实，转网络组处理', '2026-10-30 09:30:00'),
(3, NULL, 'PENDING_REVIEW', 'CREATE', 7, 'STUDENT', NULL, '2026-10-30 09:50:00'),
(3, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 2, 'MANAGER', NULL, '2026-10-30 10:00:00'),
(3, 'PENDING', 'ASSIGNED', 'ASSIGN', 2, 'MANAGER', '指派给王强', '2026-10-30 10:02:00'),
(4, NULL, 'PENDING_REVIEW', 'CREATE', 9, 'STUDENT', NULL, '2026-10-30 10:10:00'),
(4, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 3, 'MANAGER', NULL, '2026-10-30 10:20:00'),
(4, 'PENDING', 'ASSIGNED', 'ASSIGN', 3, 'MANAGER', '指派给张建国', '2026-10-30 10:22:00'),
(4, 'ASSIGNED', 'ACCEPTED', 'ACCEPT', 4, 'REPAIRER', NULL, '2026-10-30 10:35:00'),
(5, NULL, 'PENDING_REVIEW', 'CREATE', 8, 'STUDENT', NULL, '2026-10-29 14:40:00'),
(5, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 2, 'MANAGER', NULL, '2026-10-29 15:00:00'),
(5, 'PENDING', 'ASSIGNED', 'ASSIGN', 2, 'MANAGER', NULL, '2026-10-29 15:05:00'),
(5, 'ASSIGNED', 'ACCEPTED', 'ACCEPT', 5, 'REPAIRER', NULL, '2026-10-29 15:20:00'),
(5, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 5, 'REPAIRER', '预约 10-30 09:00-10:00', '2026-10-30 09:10:00'),
(6, NULL, 'PENDING_REVIEW', 'CREATE', 10, 'STUDENT', NULL, '2026-10-29 08:30:00'),
(6, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 3, 'MANAGER', '非常紧急，优先处理', '2026-10-29 09:00:00'),
(6, 'PENDING', 'ASSIGNED', 'ASSIGN', 3, 'MANAGER', NULL, '2026-10-29 09:02:00'),
(6, 'ASSIGNED', 'ACCEPTED', 'ACCEPT', 5, 'REPAIRER', NULL, '2026-10-29 09:15:00'),
(6, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 5, 'REPAIRER', NULL, '2026-10-29 10:05:00'),
(6, 'PROCESSING', 'SUSPENDED', 'SUSPEND', 5, 'REPAIRER', '缺少排水管配件，等采购', '2026-10-29 11:00:00'),
(7, NULL, 'PENDING_REVIEW', 'CREATE', 11, 'STUDENT', NULL, '2026-10-28 13:40:00'),
(7, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 2, 'MANAGER', NULL, '2026-10-28 14:00:00'),
(7, 'PENDING', 'ASSIGNED', 'ASSIGN', 2, 'MANAGER', NULL, '2026-10-28 14:05:00'),
(7, 'ASSIGNED', 'ACCEPTED', 'ACCEPT', 4, 'REPAIRER', NULL, '2026-10-28 14:20:00'),
(7, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 4, 'REPAIRER', NULL, '2026-10-29 09:05:00'),
(7, 'PROCESSING', 'PENDING_CONFIRM', 'SUBMIT_RESULT', 4, 'REPAIRER', '已更换软管', '2026-10-29 10:20:00'),
(8, NULL, 'PENDING_REVIEW', 'CREATE', 7, 'STUDENT', NULL, '2026-10-26 08:45:00'),
(8, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 2, 'MANAGER', NULL, '2026-10-26 09:00:00'),
(8, 'PENDING', 'ASSIGNED', 'ASSIGN', 2, 'MANAGER', NULL, '2026-10-26 09:05:00'),
(8, 'ASSIGNED', 'ACCEPTED', 'ACCEPT', 6, 'REPAIRER', NULL, '2026-10-26 09:20:00'),
(8, 'ACCEPTED', 'PROCESSING', 'SCHEDULE_CONFIRM', 6, 'REPAIRER', NULL, '2026-10-27 09:10:00'),
(8, 'PROCESSING', 'PENDING_CONFIRM', 'SUBMIT_RESULT', 6, 'REPAIRER', NULL, '2026-10-27 09:45:00'),
(8, 'PENDING_CONFIRM', 'COMPLETED', 'CONFIRM', 7, 'STUDENT', NULL, '2026-10-28 10:00:00'),
(9, NULL, 'PENDING_REVIEW', 'CREATE', 8, 'STUDENT', NULL, '2026-10-27 10:30:00'),
(9, 'PENDING_REVIEW', 'REJECTED', 'REVIEW_REJECT', 3, 'MANAGER', '不属于维修范围：整套家具更换需走资产采购流程。', '2026-10-27 11:00:00'),
(10, NULL, 'PENDING_REVIEW', 'CREATE', 9, 'STUDENT', NULL, '2026-10-27 15:30:00'),
(10, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 2, 'MANAGER', NULL, '2026-10-27 16:00:00'),
(10, 'PENDING', 'CANCELLED', 'CANCEL', 2, 'MANAGER', '宿舍管理规定不允许私自加装插座', '2026-10-27 16:30:00'),
(11, NULL, 'PENDING_REVIEW', 'CREATE', 10, 'STUDENT', NULL, '2026-10-28 08:05:00'),
(11, 'PENDING_REVIEW', 'PENDING', 'REVIEW_PASS', 3, 'MANAGER', NULL, '2026-10-28 08:30:00');

-- 预约
INSERT INTO repair_schedule (order_id, repairer_id, plan_date, slot_start, slot_end, status, proposed_by) VALUES
(4, 4, '2026-10-31', '09:00:00', '10:00:00', 'OFFERED',   'REPAIRER'),
(4, 4, '2026-10-31', '14:00:00', '15:00:00', 'OFFERED',   'REPAIRER'),
(5, 5, '2026-10-30', '09:00:00', '10:00:00', 'CONFIRMED', 'REPAIRER'),
(7, 4, '2026-10-29', '09:00:00', '10:00:00', 'CONFIRMED', 'REPAIRER');

-- 维修记录
INSERT INTO repair_record (order_id, repairer_id, arrive_at, start_at, end_at, content, material, result) VALUES
(7, 4, '2026-10-29 09:02:00', '2026-10-29 09:08:00', '2026-10-29 10:15:00',
 '拆开洗手台下方软管检查，发现接口处胶垫老化导致渗水，已更换软管并做防水处理。',
 'PVC 软管 1 根、生料带 1 卷', '故障已解决，无渗漏'),
(8, 6, '2026-10-27 09:05:00', '2026-10-27 09:12:00', '2026-10-27 09:40:00',
 '更换损坏的网络面板，重新打线并测试通断。',
 '网络面板 1 个、网线 1 根', '网络恢复正常'),
(5, 5, '2026-10-30 09:00:00', '2026-10-30 09:10:00', NULL,
 '门锁锁舌卡滞，拆卸清理并上油。', '润滑油', NULL);

-- 评价
INSERT INTO evaluation (order_id, student_id, repairer_id, speed_score, attitude_score, effect_score, total_score, content, anonymous) VALUES
(8, 7, 6, 5, 5, 4, 4.67, '师傅来得很快，面板换好了，就是打孔有点灰。', 0);

-- 投诉
INSERT INTO complaint (order_id, student_id, repairer_id, type, content, status) VALUES
(6, 10, 5, 'SLOW', '空调漏水报修后一直说是等配件，已经一天了没人来处理。', 'PENDING');

-- 聊天
INSERT INTO chat_message (order_id, sender_id, receiver_id, content, read_flag, created_at) VALUES
(5, 8, 5, '师傅您好，门锁还是很难开，需要我留人在宿舍吗？', 1, '2026-10-29 16:00:00'),
(5, 5, 8, '需要的，我明天上午 9 点到 10 点过去。', 1, '2026-10-29 16:10:00'),
(5, 8, 5, '好的，我在宿舍等您。', 0, '2026-10-29 16:12:00');

-- 通知
INSERT INTO notification (receiver_id, type, title, content, biz_id, read_flag, created_at) VALUES
(7, 'ORDER_STATUS', '工单已完成', '您报修的「101宿舍网络面板损坏」已确认完成，欢迎评价。', 8, 1, '2026-10-28 10:00:00'),
(11, 'ORDER_STATUS', '待确认', '您报修的「301洗手台水管漏水」维修已完成，请在 3 天内确认。', 7, 0, '2026-10-29 10:20:00'),
(5, 'ORDER_STATUS', '工单已挂起', '工单「201宿舍空调漏水」已挂起，等待配件。', 6, 1, '2026-10-29 11:00:00');

-- 信誉分流水
INSERT INTO sys_credit_log (user_id, delta, before_score, after_score, reason, biz_id, operator_id) VALUES
(8, -5, 100, 95, '预约爽约，维修人员上门无人', 5, NULL);
