-- 听写记录表（小智单方面播报，仅记录播报情况，无对错判定）
CREATE TABLE `dict_record` (
    `id` VARCHAR(32) NOT NULL COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `device_id` VARCHAR(100) COMMENT '设备ID',
    `task_id` VARCHAR(32) NOT NULL COMMENT '任务ID',
    `task_name` VARCHAR(200) COMMENT '任务名称（冗余）',
    `total_words` INT UNSIGNED NOT NULL COMMENT '总单词数',
    `words_json` JSON COMMENT '本次播报的单词列表JSON（快照，便于后续查阅）',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `duration_seconds` INT UNSIGNED COMMENT '听写时长（秒）',
    `create_date` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='听写记录';
