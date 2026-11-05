-- ============================================================
-- CampusFix 演示数据清理脚本
--
-- 用途：彻底移除 demo_seed.sql 生成的全部演示数据与 4 个演示账号。
--       仅作用于演示 ID 段（9001–9099）与演示账号，不影响基础数据
--       与真实业务数据。
--
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < db/demo/demo_cleanup.sql
-- ============================================================

USE campusfix;

-- 业务明细（按工单 ID 段清理）
DELETE FROM notification             WHERE receiver_id BETWEEN 9001 AND 9004;
DELETE FROM chat_message             WHERE sender_id BETWEEN 9001 AND 9004 OR receiver_id BETWEEN 9001 AND 9004;
DELETE FROM complaint                WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM evaluation               WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM order_extension_request  WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM repair_record            WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM repair_schedule          WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM repair_order_image       WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM repair_order_log         WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM fault_alert              WHERE id BETWEEN 9001 AND 9099;
DELETE FROM order_stall_alert        WHERE order_id BETWEEN 9001 AND 9099;
DELETE FROM profile_change_request   WHERE applicant_id BETWEEN 9001 AND 9004;
DELETE FROM sys_credit_log           WHERE user_id BETWEEN 9001 AND 9004;
DELETE FROM sys_oper_log             WHERE user_id BETWEEN 9001 AND 9004;
DELETE FROM sys_login_log            WHERE user_id BETWEEN 9001 AND 9004;

-- 工单与账号
DELETE FROM repair_order             WHERE id BETWEEN 9001 AND 9099;
DELETE FROM sys_user_role            WHERE user_id BETWEEN 9001 AND 9004;
DELETE FROM sys_user                 WHERE id BETWEEN 9001 AND 9004;

-- 校验：以下查询应全部返回 0
SELECT
  (SELECT COUNT(*) FROM repair_order WHERE id BETWEEN 9001 AND 9099)  AS demo_orders,
  (SELECT COUNT(*) FROM sys_user     WHERE id BETWEEN 9001 AND 9004)  AS demo_users,
  (SELECT COUNT(*) FROM notification WHERE receiver_id BETWEEN 9001 AND 9004) AS demo_notify;
