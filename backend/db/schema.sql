-- ============================================================
-- CampusFix 数据库结构  v1.0
-- 对应《系统设计说明书》§6 数据库结构
-- 执行：mysql -uroot -p < db/schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS campusfix
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE campusfix;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 平台域
-- ------------------------------------------------------------

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username        VARCHAR(50)     NOT NULL                COMMENT '登录账号=学号/工号',
  password        VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 哈希',
  real_name       VARCHAR(50)     NOT NULL                COMMENT '姓名',
  student_no      VARCHAR(30)     DEFAULT NULL            COMMENT '学号（学生）',
  class_name      VARCHAR(50)     DEFAULT NULL            COMMENT '班级（学生）',
  staff_no        VARCHAR(30)     DEFAULT NULL            COMMENT '工号（教职工）',
  dept_id         BIGINT UNSIGNED DEFAULT NULL            COMMENT '所属部门',
  phone           VARCHAR(20)     DEFAULT NULL            COMMENT '手机号',
  avatar          VARCHAR(255)    DEFAULT NULL            COMMENT '头像 URL',
  credit_score    INT             NOT NULL DEFAULT 100    COMMENT '信誉分（0-100）',
  status          TINYINT         NOT NULL DEFAULT 1      COMMENT '1正常 0禁用',
  pwd_reset_flag  TINYINT         NOT NULL DEFAULT 1      COMMENT '1需强制改密',
  last_login_at   DATETIME        DEFAULT NULL            COMMENT '最后登录时间',
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username   (username),
  UNIQUE KEY uk_student_no (student_no),
  UNIQUE KEY uk_staff_no   (staff_no),
  KEY idx_dept_status (dept_id, status),
  KEY idx_class (class_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code       VARCHAR(20)     NOT NULL COMMENT 'STUDENT/REPAIRER/MANAGER/ADMIN',
  name       VARCHAR(50)     NOT NULL,
  remark     VARCHAR(200)    DEFAULT NULL,
  status     TINYINT         NOT NULL DEFAULT 1,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  code       VARCHAR(50)     NOT NULL COMMENT '权限码，如 order:assign',
  name       VARCHAR(50)     NOT NULL,
  type       VARCHAR(10)     NOT NULL COMMENT 'MENU/BUTTON/API',
  path       VARCHAR(200)    DEFAULT NULL COMMENT '前端路由或接口路径',
  component  VARCHAR(200)    DEFAULT NULL,
  icon       VARCHAR(50)     DEFAULT NULL,
  sort       INT             NOT NULL DEFAULT 0,
  status     TINYINT         NOT NULL DEFAULT 1,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_parent (parent_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  user_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (user_id, role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
  role_id       BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  KEY idx_perm (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  name       VARCHAR(50)     NOT NULL,
  code       VARCHAR(30)     DEFAULT NULL,
  leader     VARCHAR(50)     DEFAULT NULL,
  phone      VARCHAR(20)     DEFAULT NULL,
  type       VARCHAR(20)     NOT NULL DEFAULT 'REPAIR' COMMENT 'REPAIR 维修部门 / ADMIN 行政',
  sort       INT             NOT NULL DEFAULT 0,
  status     TINYINT         NOT NULL DEFAULT 1,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  config_key   VARCHAR(80)     NOT NULL,
  config_value VARCHAR(255)    NOT NULL,
  value_type   VARCHAR(20)     NOT NULL DEFAULT 'STRING' COMMENT 'STRING/INT/DECIMAL/BOOL',
  remark       VARCHAR(200)    DEFAULT NULL,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数';

DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED DEFAULT NULL,
  username      VARCHAR(50)     DEFAULT NULL,
  module        VARCHAR(50)     DEFAULT NULL COMMENT '模块',
  operation     VARCHAR(100)    DEFAULT NULL COMMENT '操作描述',
  method        VARCHAR(200)    DEFAULT NULL,
  request_uri   VARCHAR(255)    DEFAULT NULL,
  request_param VARCHAR(2000)   DEFAULT NULL COMMENT '参数（脱敏）',
  ip            VARCHAR(50)     DEFAULT NULL,
  status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
  error_msg     VARCHAR(500)    DEFAULT NULL,
  cost_ms       INT             DEFAULT NULL,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at),
  KEY idx_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED DEFAULT NULL,
  username   VARCHAR(50)     NOT NULL,
  ip         VARCHAR(50)     DEFAULT NULL,
  user_agent VARCHAR(255)    DEFAULT NULL,
  status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
  msg        VARCHAR(200)    DEFAULT NULL,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

DROP TABLE IF EXISTS sys_credit_log;
CREATE TABLE sys_credit_log (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  delta        INT             NOT NULL COMMENT '正数加分，负数扣分',
  before_score INT             NOT NULL,
  after_score  INT             NOT NULL,
  reason       VARCHAR(200)    NOT NULL,
  biz_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单',
  operator_id  BIGINT UNSIGNED DEFAULT NULL COMMENT 'NULL 表示系统自动',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信誉分流水';

-- ------------------------------------------------------------
-- 资产域
-- ------------------------------------------------------------

DROP TABLE IF EXISTS repair_category;
CREATE TABLE repair_category (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name       VARCHAR(50)     NOT NULL,
  code       VARCHAR(30)     NOT NULL,
  dept_id    BIGINT UNSIGNED NOT NULL COMMENT '责任部门',
  sort       INT             NOT NULL DEFAULT 0,
  status     TINYINT         NOT NULL DEFAULT 1,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障类型';

DROP TABLE IF EXISTS location;
CREATE TABLE location (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  level      TINYINT         NOT NULL COMMENT '1校区 2区域 3楼栋 4楼层 5房间',
  name       VARCHAR(50)     NOT NULL,
  code       VARCHAR(50)     NOT NULL COMMENT '如 C01-SS-B02-F03-R301',
  type       VARCHAR(20)     NOT NULL COMMENT 'CAMPUS/AREA/BUILDING/FLOOR/ROOM',
  sort       INT             NOT NULL DEFAULT 0,
  status     TINYINT         NOT NULL DEFAULT 1,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_parent (parent_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='位置树';

DROP TABLE IF EXISTS facility_type;
CREATE TABLE facility_type (
  id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name   VARCHAR(50)     NOT NULL,
  code   VARCHAR(30)     NOT NULL,
  sort   INT             NOT NULL DEFAULT 0,
  status TINYINT         NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设施类型';

DROP TABLE IF EXISTS facility;
CREATE TABLE facility (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  facility_no  VARCHAR(30)     NOT NULL COMMENT '如 AC-2026-00128',
  name         VARCHAR(50)     NOT NULL,
  type_id      BIGINT UNSIGNED NOT NULL,
  location_id  BIGINT UNSIGNED NOT NULL,
  install_date DATE            DEFAULT NULL,
  status       VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/REPAIRING/SCRAPPED',
  remark       VARCHAR(255)    DEFAULT NULL,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_facility_no (facility_no),
  KEY idx_location (location_id),
  KEY idx_type (type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设施档案';

DROP TABLE IF EXISTS fault_alert;
CREATE TABLE fault_alert (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  building_id  BIGINT UNSIGNED NOT NULL,
  category_id  BIGINT UNSIGNED NOT NULL,
  order_count  INT             NOT NULL,
  window_start DATE            NOT NULL,
  window_end   DATE            NOT NULL,
  status       VARCHAR(20)     NOT NULL DEFAULT 'NEW' COMMENT 'NEW/READ/HANDLED',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_day_combo (building_id, category_id, window_end),
  KEY idx_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高频故障预警';

-- ------------------------------------------------------------
-- 工单域
-- ------------------------------------------------------------

DROP TABLE IF EXISTS repair_order;
CREATE TABLE repair_order (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_no          VARCHAR(20)     NOT NULL                COMMENT '业务单号 yyyyMMdd+3位',
  reporter_id       BIGINT UNSIGNED NOT NULL                COMMENT '报修人（任意角色）',
  reporter_phone    VARCHAR(20)     DEFAULT NULL            COMMENT '报修人手机号快照',
  title             VARCHAR(100)    NOT NULL,
  description       VARCHAR(1000)   NOT NULL,
  category_id       BIGINT UNSIGNED NOT NULL                COMMENT '故障类型',
  facility_id       BIGINT UNSIGNED DEFAULT NULL            COMMENT '关联设施（可空）',
  location_id       BIGINT UNSIGNED NOT NULL                COMMENT '位置节点',
  location_path     VARCHAR(255)    NOT NULL                COMMENT '位置全路径快照',
  building_id       BIGINT UNSIGNED NOT NULL                COMMENT '所属楼栋（冗余）',
  urgency           TINYINT         NOT NULL DEFAULT 1      COMMENT '1普通 2紧急 3非常紧急',
  priority          TINYINT         NOT NULL DEFAULT 1      COMMENT '优先级 0-9',
  status            VARCHAR(20)     NOT NULL                COMMENT '见状态枚举',
  dept_id           BIGINT UNSIGNED NOT NULL                COMMENT '责任部门',
  assignee_id       BIGINT UNSIGNED DEFAULT NULL            COMMENT '当前维修人',
  auto_assigned     TINYINT         NOT NULL DEFAULT 0      COMMENT '1=24h自动派单',
  is_warranty       TINYINT         NOT NULL DEFAULT 0      COMMENT '1=质保返修单',
  parent_order_id   BIGINT UNSIGNED DEFAULT NULL            COMMENT '父工单',
  rework_flag       TINYINT         NOT NULL DEFAULT 0      COMMENT '1=当前为返修流程',
  rework_count      INT             NOT NULL DEFAULT 0      COMMENT '返修次数',
  review_by         BIGINT UNSIGNED DEFAULT NULL,
  review_at         DATETIME        DEFAULT NULL            COMMENT '审核通过时间（24h 倒计时起点）',
  reject_reason     VARCHAR(255)    DEFAULT NULL,
  accepted_at       DATETIME        DEFAULT NULL            COMMENT '接单时间',
  arrived_at        DATETIME        DEFAULT NULL            COMMENT '到达时间',
  started_at        DATETIME        DEFAULT NULL            COMMENT '开始维修时间',
  finished_at       DATETIME        DEFAULT NULL            COMMENT '完成时间（统计基准）',
  confirmed_at      DATETIME        DEFAULT NULL            COMMENT '学生确认时间',
  cancelled_at      DATETIME        DEFAULT NULL,
  suspend_start_at  DATETIME        DEFAULT NULL,
  suspend_minutes   INT             NOT NULL DEFAULT 0      COMMENT '累计挂起分钟',
  confirm_deadline  DATETIME        DEFAULT NULL            COMMENT '自动验收截止时间',
  auto_confirmed    TINYINT         NOT NULL DEFAULT 0,
  plan_time         DATETIME        DEFAULT NULL            COMMENT '确认的上门时间',
  version           INT             NOT NULL DEFAULT 0      COMMENT '乐观锁',
  created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted           TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status_created (status, created_at),
  KEY idx_reporter (reporter_id, created_at),
  KEY idx_assignee (assignee_id, status),
  KEY idx_pool (status, priority DESC, created_at),
  KEY idx_building_cat (building_id, category_id, created_at),
  KEY idx_dept_status (dept_id, status),
  KEY idx_confirm_deadline (status, confirm_deadline),
  KEY idx_finished (finished_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单主表';

DROP TABLE IF EXISTS repair_order_log;
CREATE TABLE repair_order_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id      BIGINT UNSIGNED NOT NULL,
  from_status   VARCHAR(20)     DEFAULT NULL COMMENT '初始创建为 NULL',
  to_status     VARCHAR(20)     NOT NULL,
  action        VARCHAR(30)     NOT NULL COMMENT 'OrderAction 枚举',
  operator_id   BIGINT UNSIGNED NOT NULL,
  operator_role VARCHAR(20)     NOT NULL,
  remark        VARCHAR(500)    DEFAULT NULL,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单状态流转日志';

DROP TABLE IF EXISTS repair_order_image;
CREATE TABLE repair_order_image (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id    BIGINT UNSIGNED NOT NULL,
  stage       VARCHAR(20)     NOT NULL COMMENT 'REPORT/BEFORE/AFTER',
  object_key  VARCHAR(255)    NOT NULL COMMENT '存储键，不存完整 URL',
  file_name   VARCHAR(100)    DEFAULT NULL,
  file_size   INT             DEFAULT NULL COMMENT '字节',
  sort        INT             NOT NULL DEFAULT 0,
  uploader_id BIGINT UNSIGNED NOT NULL,
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order_stage (order_id, stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单图片';

DROP TABLE IF EXISTS repair_schedule;
CREATE TABLE repair_schedule (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id    BIGINT UNSIGNED NOT NULL,
  repairer_id BIGINT UNSIGNED NOT NULL,
  plan_date   DATE            NOT NULL,
  slot_start  TIME            NOT NULL,
  slot_end    TIME            NOT NULL,
  status      VARCHAR(20)     NOT NULL COMMENT 'OFFERED/SELECTED/CONFIRMED/CANCELLED',
  proposed_by VARCHAR(20)     NOT NULL COMMENT 'REPAIRER/STUDENT',
  remark      VARCHAR(255)    DEFAULT NULL,
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order (order_id, status),
  KEY idx_repairer_date (repairer_id, plan_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修预约时段';

DROP TABLE IF EXISTS repair_record;
CREATE TABLE repair_record (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id    BIGINT UNSIGNED NOT NULL,
  repairer_id BIGINT UNSIGNED NOT NULL,
  arrive_at   DATETIME DEFAULT NULL,
  start_at    DATETIME DEFAULT NULL,
  end_at      DATETIME DEFAULT NULL,
  content     VARCHAR(1000) DEFAULT NULL COMMENT '维修内容',
  material    VARCHAR(500)  DEFAULT NULL COMMENT '使用材料',
  result      VARCHAR(500)  DEFAULT NULL COMMENT '维修结果',
  remark      VARCHAR(500)  DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修过程记录';

DROP TABLE IF EXISTS evaluation;
CREATE TABLE evaluation (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id       BIGINT UNSIGNED NOT NULL,
  student_id     BIGINT UNSIGNED NOT NULL,
  repairer_id    BIGINT UNSIGNED NOT NULL,
  speed_score    TINYINT NOT NULL COMMENT '维修速度 1-5',
  attitude_score TINYINT NOT NULL COMMENT '服务态度 1-5',
  effect_score   TINYINT NOT NULL COMMENT '维修效果 1-5',
  total_score    DECIMAL(3,2) NOT NULL COMMENT '加权总分',
  content        VARCHAR(500) DEFAULT NULL,
  anonymous      TINYINT NOT NULL DEFAULT 0,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order (order_id),
  KEY idx_repairer (repairer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价';

DROP TABLE IF EXISTS complaint;
CREATE TABLE complaint (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id      BIGINT UNSIGNED NOT NULL,
  student_id    BIGINT UNSIGNED NOT NULL,
  repairer_id   BIGINT UNSIGNED DEFAULT NULL,
  type          VARCHAR(20) NOT NULL COMMENT 'ATTITUDE/SLOW/QUALITY/OTHER',
  content       VARCHAR(1000) NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/REJECTED',
  handler_id    BIGINT UNSIGNED DEFAULT NULL,
  handle_result VARCHAR(500) DEFAULT NULL,
  handled_at    DATETIME DEFAULT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status (status, created_at),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉';

DROP TABLE IF EXISTS chat_message;
CREATE TABLE chat_message (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id    BIGINT UNSIGNED NOT NULL,
  sender_id   BIGINT UNSIGNED NOT NULL,
  receiver_id BIGINT UNSIGNED NOT NULL,
  content     VARCHAR(500) NOT NULL,
  read_flag   TINYINT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order_id (order_id, id),
  KEY idx_receiver_read (receiver_id, read_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单维度聊天消息';

DROP TABLE IF EXISTS notification;
CREATE TABLE notification (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  receiver_id BIGINT UNSIGNED NOT NULL,
  type        VARCHAR(30) NOT NULL COMMENT 'ORDER_STATUS/CHAT/COMPLAINT/SYSTEM',
  title       VARCHAR(100) NOT NULL,
  content     VARCHAR(500) NOT NULL,
  biz_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '关联业务ID（通常为工单）',
  read_flag   TINYINT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_receiver (receiver_id, read_flag, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

DROP TABLE IF EXISTS order_extension_request;
CREATE TABLE order_extension_request (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id      BIGINT UNSIGNED NOT NULL,
  student_id    BIGINT UNSIGNED NOT NULL,
  reason        VARCHAR(500) NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  handler_id    BIGINT UNSIGNED DEFAULT NULL,
  handle_result VARCHAR(500) DEFAULT NULL,
  handled_at    DATETIME DEFAULT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='延期确认申请';

SET FOREIGN_KEY_CHECKS = 1;
