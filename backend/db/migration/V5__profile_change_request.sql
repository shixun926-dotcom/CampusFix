-- ============================================================
-- V5：维修工「个人中心」字段权限与信息更改申请
--   1) sys_user 增加 avatar_deadline（头像强制更换截止时间）
--   2) 新增 profile_change_request（信息更改申请）表
-- 执行：mysql -uroot -p campusfix < db/migration/V5__profile_change_request.sql
-- 前置：已执行过 db/schema.sql（V1）
-- 注意：现行 db/schema.sql 已内置本表，全新库请勿执行本脚本；
--       本脚本仅用于「V5 之前建库」的旧环境升级。
-- ============================================================

USE campusfix;

-- 1) sys_user 增加头像强制更换截止时间
ALTER TABLE sys_user
  ADD COLUMN avatar_deadline DATETIME DEFAULT NULL COMMENT '头像强制更换截止时间（逾期未换则提示）'
  AFTER avatar;

-- 2) 信息更改申请表（存在则跳过）
CREATE TABLE IF NOT EXISTS profile_change_request (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  applicant_id  BIGINT UNSIGNED NOT NULL COMMENT '申请人（维修工）',
  field_key     VARCHAR(30)  NOT NULL COMMENT '字段标识 real_name/staff_no/dept_name',
  field_label   VARCHAR(30)  NOT NULL COMMENT '字段中文名',
  old_value     VARCHAR(100) DEFAULT NULL COMMENT '原值',
  new_value     VARCHAR(100) NOT NULL COMMENT '申请的新值',
  reason        VARCHAR(500) NOT NULL COMMENT '申请理由',
  status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  reviewer_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '审核人（后勤管理员）',
  reject_reason VARCHAR(500) DEFAULT NULL COMMENT '打回原因',
  reviewed_at   DATETIME DEFAULT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_applicant (applicant_id, status, created_at),
  KEY idx_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修工信息更改申请';

-- 3) 信息更改审核权限（id=62），授权给后勤管理员角色（role_id=3）
INSERT INTO sys_permission (id, parent_id, code, name, type, path, sort, status)
SELECT 62, 0, 'profile:change:review', '信息更改审核', 'BUTTON', NULL, 62, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'profile:change:review');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, p.id FROM sys_permission p
WHERE p.code = 'profile:change:review'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = 3 AND rp.permission_id = p.id
  );
