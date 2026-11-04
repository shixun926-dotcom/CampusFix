-- ============================================================
-- V4：新增「个人中心」权限 profile:view，并授权给维修人员角色
-- 执行：mysql -uroot -p campusfix < db/migration/V4__profile_permission.sql
-- 前置：已执行过 db/schema.sql（V1）
-- 注意：现行 db/data.sql 已内置本权限（id=61），全新库无需执行本脚本；
--       本脚本仅用于「V4 之前建库」的旧环境升级。
-- ============================================================

USE campusfix;

-- 权限（已存在则跳过；ID 用固定 61 便于回滚定位）
INSERT INTO sys_permission (id, parent_id, code, name, type, path, sort, status)
SELECT 61, 0, 'profile:view', '个人中心', 'API', '/profile', 61, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'profile:view');

-- 授权给维修人员角色（role_id=2，对应 code=REPAIRER）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, p.id FROM sys_permission p
WHERE p.code = 'profile:view'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = 2 AND rp.permission_id = p.id
  );
