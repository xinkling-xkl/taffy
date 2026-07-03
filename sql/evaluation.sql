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

 Date: 16/06/2026 19:14:38
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for evaluation
-- ----------------------------
DROP TABLE IF EXISTS `evaluation`;
CREATE TABLE `evaluation`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `evaluator_id` int UNSIGNED NOT NULL COMMENT '评价人ID',
  `evaluated_id` int UNSIGNED NOT NULL COMMENT '被评价人ID',
  `job_id` int NOT NULL COMMENT '兼职ID',
  `work_assignment_id` int UNSIGNED NOT NULL COMMENT '关联的工作分配ID',
  `rating` int NOT NULL COMMENT '星级：1-5星',
  `is_positive` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否好评',
  `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '评价内容',
  `reply` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '商户回复',
  `reply_time` datetime NULL DEFAULT NULL COMMENT '回复时间',
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评价类型：student_to_merchant, merchant_to_student',
  `credit_impact` int NULL DEFAULT 0 COMMENT '信用分影响',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_evaluator`(`evaluator_id` ASC) USING BTREE,
  INDEX `idx_evaluated`(`evaluated_id` ASC) USING BTREE,
  INDEX `idx_job`(`job_id` ASC) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  CONSTRAINT `evaluation_ibfk_1` FOREIGN KEY (`evaluator_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `evaluation_ibfk_2` FOREIGN KEY (`evaluated_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `evaluation_ibfk_3` FOREIGN KEY (`job_id`) REFERENCES `job` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '评价表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
