-- 创建学生时间偏好表
CREATE TABLE `student_schedule` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `student_id` INT UNSIGNED NOT NULL COMMENT '学生ID',
  `day_of_week` TINYINT NOT NULL COMMENT '星期几：1=周一，2=周二...7=周日',
  `time_slot` VARCHAR(20) NOT NULL COMMENT '时间段：morning-早上，afternoon-下午，evening-晚上',
  `is_available` TINYINT NOT NULL DEFAULT 1 COMMENT '是否空闲：0-不可用，1-可用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_day_slot`(`student_id`, `day_of_week`, `time_slot`),
  INDEX `idx_student`(`student_id`),
  INDEX `idx_day_slot`(`day_of_week`, `time_slot`),
  CONSTRAINT `student_schedule_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COMMENT = '学生时间安排表';

-- 创建商户工作时间需求表
CREATE TABLE `merchant_requirement` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `job_id` INT NOT NULL COMMENT '兼职ID',
  `merchant_id` INT UNSIGNED NOT NULL COMMENT '商户ID',
  `day_of_week` TINYINT NOT NULL COMMENT '星期几：1=周一...7=周日',
  `time_slot` VARCHAR(20) NOT NULL COMMENT '时间段：morning/afternoon/evening',
  `needed_count` INT NOT NULL DEFAULT 1 COMMENT '需要人数',
  `filled_count` INT NOT NULL DEFAULT 0 COMMENT '已安排人数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_job`(`job_id`),
  INDEX `idx_day_slot`(`day_of_week`, `time_slot`),
  CONSTRAINT `merchant_requirement_ibfk_2` FOREIGN KEY (`merchant_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COMMENT = '商户工作时间需求表';

-- 创建工作分配表
CREATE TABLE `work_assignment` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `student_id` INT UNSIGNED NOT NULL COMMENT '学生ID',
  `job_id` INT NOT NULL COMMENT '兼职ID',
  `merchant_requirement_id` INT UNSIGNED NULL COMMENT '对应的需求ID',
  `work_date` DATE NOT NULL COMMENT '工作日期',
  `time_slot` VARCHAR(20) NOT NULL COMMENT '时间段：morning/afternoon/evening',
  `status` VARCHAR(20) NOT NULL DEFAULT 'assigned' COMMENT '状态：assigned-已分配，completed-已完成，cancelled-已取消',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_student`(`student_id`),
  INDEX `idx_job`(`job_id`),
  INDEX `idx_date_slot`(`work_date`, `time_slot`),
  CONSTRAINT `work_assignment_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COMMENT = '学生工作分配表';

-- 创建签到记录表
CREATE TABLE `attendance` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `student_id` INT UNSIGNED NOT NULL COMMENT '学生ID',
  `work_assignment_id` INT UNSIGNED NOT NULL COMMENT '工作分配ID',
  `work_date` DATE NOT NULL COMMENT '工作日期',
  `check_in_time` DATETIME NULL DEFAULT NULL COMMENT '签到时间',
  `check_out_time` DATETIME NULL DEFAULT NULL COMMENT '签退时间',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending-待签到，checked_in-已签到，late-迟到，absent-缺勤，completed-已完成',
  `is_late` TINYINT NOT NULL DEFAULT 0 COMMENT '是否迟到：0-否，1-是',
  `late_minutes` INT NULL DEFAULT 0 COMMENT '迟到分钟数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_student`(`student_id`),
  INDEX `idx_assignment`(`work_assignment_id`),
  INDEX `idx_work_date`(`work_date`),
  INDEX `idx_status`(`status`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COMMENT = '签到记录表';

-- 创建评价表（互评）
CREATE TABLE `evaluation` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `evaluator_id` INT UNSIGNED NOT NULL COMMENT '评价人ID',
  `evaluated_id` INT UNSIGNED NOT NULL COMMENT '被评价人ID',
  `job_id` INT NOT NULL COMMENT '兼职ID',
  `work_assignment_id` INT UNSIGNED NOT NULL COMMENT '工作分配ID',
  `rating` TINYINT NOT NULL COMMENT '评分：1-5分',
  `comment` TEXT NULL COMMENT '评价内容',
  `is_positive` TINYINT NOT NULL DEFAULT 1 COMMENT '是否正面评价：0-差评，1-好评',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_evaluator`(`evaluator_id`),
  INDEX `idx_evaluated`(`evaluated_id`),
  INDEX `idx_job`(`job_id`),
  CONSTRAINT `evaluation_ibfk_1` FOREIGN KEY (`evaluator_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `evaluation_ibfk_2` FOREIGN KEY (`evaluated_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COMMENT = '评价表';

-- 创建信用记录表
CREATE TABLE `credit_record` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
  `type` VARCHAR(50) NOT NULL COMMENT '记录类型：attendance-考勤，completion-完成工作，evaluation-评价',
  `credit_change` INT NOT NULL COMMENT '信用分变更：正数为增加，负数为减少',
  `reason` VARCHAR(255) NOT NULL COMMENT '变更原因',
  `related_id` INT UNSIGNED NULL COMMENT '相关ID（如attendance_id、evaluation_id）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user`(`user_id`),
  INDEX `idx_type`(`type`),
  CONSTRAINT `credit_record_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COMMENT = '信用记录表';