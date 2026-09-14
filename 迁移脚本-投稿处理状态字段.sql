-- ============================================================
-- 迁移脚本：用户投稿增加「处理状态」字段（配合异步处理图片）
-- 目标库：interview
-- 影响表：user_upload
-- 用途  ：投稿提交后先落库并立刻返回，图片搬运/正文渲染放到线程池异步做；
--         前端据此显示「处理中 / 已完成 / 处理失败」。
-- 执行  ：mysql -uroot -p interview < 迁移脚本-投稿处理状态字段.sql
--         或在宝塔面板 / Navicat 的 SQL 窗口里整段执行。
--
-- 说明  ：
--   1. 默认值 1（已完成），所以**历史数据自动是"已完成"**，符合事实
--      （老数据是同步处理完才入库的）。
--   2. 新投稿由后端写入 0（处理中），异步任务完成后改成 1，失败改成 2。
--   3. 先执行本脚本，再部署新版本后端。
--   4. 重复执行会报 1060 Duplicate column（属正常）。
-- ============================================================

USE `interview`;

ALTER TABLE `user_upload`
  ADD COLUMN `process_status` tinyint NOT NULL DEFAULT 1
    COMMENT '处理状态：0=处理中 1=已完成 2=处理失败'
    AFTER `status`;

-- 自检：应返回 1 行
SELECT
  TABLE_NAME     AS '表名',
  COLUMN_NAME    AS '字段名',
  COLUMN_TYPE    AS '类型',
  COLUMN_DEFAULT AS '默认值',
  COLUMN_COMMENT AS '注释'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user_upload'
  AND COLUMN_NAME = 'process_status';

-- 看看当前投稿的处理状态分布（历史数据应全是 1）
SELECT process_status, COUNT(*) AS cnt FROM `user_upload` GROUP BY process_status;

-- ============================================================
-- 回滚（需要时手动放开注释执行）
-- ============================================================
-- ALTER TABLE `user_upload` DROP COLUMN `process_status`;
