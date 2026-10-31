-- ============================================================
-- V2：用户表增加「班级」字段（学生导入需要）
-- 执行：mysql -uroot -p campusfix < db/migration/V2__student_class.sql
-- 前置：已执行过 db/schema.sql（V1）
-- ============================================================

USE campusfix;

ALTER TABLE sys_user
  ADD COLUMN class_name VARCHAR(50) DEFAULT NULL COMMENT '班级（学生）' AFTER student_no,
  ADD KEY idx_class (class_name);

-- 为已有学生补一个默认班级，便于列表展示
UPDATE sys_user SET class_name = '未分班' WHERE student_no IS NOT NULL AND class_name IS NULL;
