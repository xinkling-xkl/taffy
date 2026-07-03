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

 Date: 16/06/2026 19:15:30
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for student_schedule
-- ----------------------------
DROP TABLE IF EXISTS `student_schedule`;
CREATE TABLE `student_schedule`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `student_id` int UNSIGNED NOT NULL COMMENT '学生ID',
  `day_of_week` tinyint NOT NULL COMMENT '星期几：1=周一，2=周二...7=周日',
  `time_slot` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '时间段：morning-早上，afternoon-下午，evening-晚上',
  `is_available` tinyint NOT NULL DEFAULT 1 COMMENT '是否空闲：0-不可用，1-可用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_student_day_slot`(`student_id` ASC, `day_of_week` ASC, `time_slot` ASC) USING BTREE,
  INDEX `idx_student`(`student_id` ASC) USING BTREE,
  INDEX `idx_day_slot`(`day_of_week` ASC, `time_slot` ASC) USING BTREE,
  CONSTRAINT `student_schedule_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 779 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生时间安排表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
