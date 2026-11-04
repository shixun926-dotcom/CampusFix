-- ============================================================
-- V3：新增「滞留工单推送去重」表 order_stall_alert
-- 执行：mysql -uroot -p campusfix < db/migration/V3__order_stall_alert.sql
-- 前置：已执行过 db/schema.sql（V1）
-- 注意：现行 db/schema.sql 已内置本表，全新库请勿执行本脚本，
--       否则报 ERROR 1050 Table 'order_stall_alert' already exists；
--       本脚本仅用于「V3 之前建库」的旧环境升级，且不可重复执行。
-- ============================================================

USE campusfix;

CREATE TABLE IF NOT EXISTS order_stall_alert (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id       BIGINT UNSIGNED NOT NULL,
  order_no       VARCHAR(32)     NOT NULL,
  status         VARCHAR(30)     NOT NULL COMMENT '推送时的工单状态',
  stall_since    DATETIME        NOT NULL COMMENT '判定滞留时的最后更新时间',
  notified_count INT             NOT NULL DEFAULT 1 COMMENT '已推送次数',
  last_notified_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_status (order_id, status),
  KEY idx_last_notified (last_notified_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='滞留工单推送去重（同工单同状态只推一次）';
