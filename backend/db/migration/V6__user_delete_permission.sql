-- ============================================================
-- V6：用户删除 / 批量删除权限
--   1) sys_permission 新增 user:delete（删除用户）
--   2) 授予系统管理员（role_id = 4）该权限
-- 执行：mysql -uroot -p campusfix < db/migration/V6__user_delete_permission.sql
-- 前置：已执行过 db/schema.sql（V1）与 db/data.sql（含角色与权限种子）
-- 注意：现行 db/data.sql 已内置本权限，全新库无需执行本脚本；
--       本脚本仅用于「V6 之前建库」的旧环境升级，重复执行会因主键冲突报错。
-- ============================================================

USE campusfix;

-- 权限点：删除用户（单条与批量共用同一权限）
INSERT INTO sys_permission (id, parent_id, code, name, type, path, sort)
VALUES (63, 0, 'user:delete', '删除用户', 'BUTTON', NULL, 53);

-- 仅系统管理员可删除成员；后勤管理员（3）不授予，避免跨部门误删
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (4, 63);
