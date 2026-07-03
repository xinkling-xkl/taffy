/*
 Navicat Premium Dump SQL

 Source Server         : taffy
 Source Server Type    : MySQL
 Source Server Version : 90001 (9.0.1)
 Source Host           : localhost:3306
 Source Schema         : test

 Target Server Type    : MySQL
 Target Server Version : 90001 (9.0.1)
 File Encoding         : 65001

 Date: 16/06/2026 19:15:02
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for job_application
-- ----------------------------
DROP TABLE IF EXISTS `job_application`;
CREATE TABLE `job_application`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `job_id` int NOT NULL COMMENT '兼职ID',
  `student_id` int UNSIGNED NOT NULL COMMENT '学生ID',
  `merchant_id` int UNSIGNED NOT NULL COMMENT '商户ID',
  `student_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '学生姓名',
  `time_availability` json NOT NULL COMMENT '时间安排',
  `credit_score` int NOT NULL COMMENT '信用分',
  `application_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '申请内容',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'pending' COMMENT '状态：pending-待处理，accepted-已接受，rejected-已拒绝',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '商户留言或拒绝原因',
  `selected_dates` json NULL COMMENT '日结申请的日期列表，如 [{\"date\":\"2026-05-11\",\"timeSlot\":\"morning\"},...]',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_job`(`job_id` ASC) USING BTREE,
  INDEX `idx_student`(`student_id` ASC) USING BTREE,
  INDEX `idx_merchant`(`merchant_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `job_application_ibfk_1` FOREIGN KEY (`job_id`) REFERENCES `job` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `job_application_ibfk_2` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `job_application_ibfk_3` FOREIGN KEY (`merchant_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 141 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '兼职申请表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
