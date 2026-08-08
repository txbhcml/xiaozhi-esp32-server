-- 听写任务表新增"重复播报间隔"字段
ALTER TABLE `dict_task` ADD COLUMN `repeat_interval_seconds` DECIMAL(4,1) NOT NULL DEFAULT 1.0 COMMENT '重复播报同一单词的间隔（秒）' AFTER `speak_rate`;
