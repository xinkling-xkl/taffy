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

 Date: 16/06/2026 19:15:50
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for work_assignment
-- ----------------------------
DROP TABLE IF EXISTS `work_assignment`;
CREATE TABLE `work_assignment`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `student_id` int UNSIGNED NOT NULL COMMENT '学生ID',
  `job_id` int NOT NULL COMMENT '兼职ID',
  `merchant_requirement_id` int UNSIGNED NOT NULL COMMENT '对应的需求ID',
  `work_date` date NOT NULL COMMENT '工作日期',
  `time_slot` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '时间段：morning/afternoon/evening',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'assigned' COMMENT '状态：assigned-已分配，completed-已完成，cancelled-已取消',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `can_check_out` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可签退：0-不可签退，1-可签退（商户确认后）',
  `merchant_settled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '商户是否已结算：0-未结算，1-已结算',
  `settle_time` datetime NULL DEFAULT NULL COMMENT '结算时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_student`(`student_id` ASC) USING BTREE,
  INDEX `idx_job`(`job_id` ASC) USING BTREE,
  INDEX `idx_date_slot`(`work_date` ASC, `time_slot` ASC) USING BTREE,
  CONSTRAINT `work_assignment_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 864 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生工作分配表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
