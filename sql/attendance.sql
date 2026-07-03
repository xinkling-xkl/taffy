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

 Date: 16/06/2026 19:14:27
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for attendance
-- ----------------------------
DROP TABLE IF EXISTS `attendance`;
CREATE TABLE `attendance`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `work_assignment_id` int UNSIGNED NOT NULL COMMENT '工作分配ID',
  `student_id` int UNSIGNED NOT NULL COMMENT '学生ID',
  `check_in_time` datetime NULL DEFAULT NULL COMMENT '签到时间',
  `check_out_time` datetime NULL DEFAULT NULL COMMENT '签退时间',
  `is_late` tinyint NOT NULL DEFAULT 0 COMMENT '是否迟到：0-否，1-是',
  `late_minutes` int NOT NULL DEFAULT 0 COMMENT '迟到分钟数',
  `is_absent` tinyint NOT NULL DEFAULT 0 COMMENT '是否缺勤：0-否，1-是',
  `absent_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '缺勤原因',
  `work_minutes` int NOT NULL DEFAULT 0 COMMENT '工作分钟数',
  `attendance_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '考勤状态：pending-待签到，checked_in-已签到，checked_out-已签退，late-迟到，absent-缺勤，early_leave-提前下班，completed-已完成',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_work_assignment`(`work_assignment_id` ASC) USING BTREE,
  INDEX `idx_student`(`student_id` ASC) USING BTREE,
  INDEX `idx_date_status`(`created_at` ASC, `attendance_status` ASC) USING BTREE,
  CONSTRAINT `attendance_ibfk_1` FOREIGN KEY (`work_assignment_id`) REFERENCES `work_assignment` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `attendance_ibfk_2` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 42 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '考勤记录表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
