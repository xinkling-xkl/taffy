CREATE TABLE IF NOT EXISTS `message` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `sender_id` INT UNSIGNED NOT NULL COMMENT '发送者ID',
  `receiver_id` INT UNSIGNED NOT NULL COMMENT '接收者ID',
  `type` VARCHAR(50) NOT NULL COMMENT '消息类型',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `is_read` TINYINT NULL DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
  `related_id` INT UNSIGNED NULL DEFAULT NULL COMMENT '关联ID',
  `created_at` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_receiver`(`receiver_id`),
  INDEX `idx_is_read`(`is_read`),
  INDEX `idx_sender`(`sender_id`),
  CONSTRAINT `message_ibfk_1` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `message_ibfk_2` FOREIGN KEY (`receiver_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COMMENT = '消息表';
