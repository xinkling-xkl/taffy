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

 Date: 16/06/2026 19:15:19
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for merchant_requirement
-- ----------------------------
DROP TABLE IF EXISTS `merchant_requirement`;
CREATE TABLE `merchant_requirement`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `job_id` int NOT NULL COMMENT '兼职ID',
  `merchant_id` int UNSIGNED NOT NULL COMMENT '商户ID',
  `day_of_week` tinyint NOT NULL COMMENT '星期几：1=周一...7=周日',
  `time_slot` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '时间段：morning/afternoon/evening',
  `needed_count` int NOT NULL DEFAULT 1 COMMENT '需要人数',
  `filled_count` int NOT NULL DEFAULT 0 COMMENT '已安排人数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_job`(`job_id` ASC) USING BTREE,
  INDEX `idx_day_slot`(`day_of_week` ASC, `time_slot` ASC) USING BTREE,
  INDEX `merchant_requirement_ibfk_2`(`merchant_id` ASC) USING BTREE,
  CONSTRAINT `merchant_requirement_ibfk_2` FOREIGN KEY (`merchant_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1428 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商户工作时间需求表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
