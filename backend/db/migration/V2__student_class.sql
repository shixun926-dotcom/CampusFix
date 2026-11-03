-- ============================================================
-- V2：用户表增加「班级」字段（学生导入需要）
-- 执行：mysql -uroot -p campusfix < db/migration/V2__student_class.sql
-- 前置：已执行过 db/schema.sql（V1）
-- 注意：现行 db/schema.sql 的 sys_user 已内置 class_name 列，
--       全新库请勿执行本脚本，否则报 ERROR 1060 Duplicate column name；
--       本脚本仅用于「V2 之前建库」的旧环境升级，且不可重复执行。
-- ============================================================

USE campusfix;

ALTER TABLE sys_user
  ADD COLUMN class_name VARCHAR(50) DEFAULT NULL COMMENT '班级（学生）' AFTER student_no,
  ADD KEY idx_class (class_name);

-- 为已有学生补一个默认班级，便于列表展示
UPDATE sys_user SET class_name = '未分班' WHERE student_no IS NOT NULL AND class_name IS NULL;
