-- 添加实名认证相关字段到user表
ALTER TABLE `user` 
ADD COLUMN `rname` varchar(255) DEFAULT NULL COMMENT '真实姓名' AFTER `name`,
ADD COLUMN `number` varchar(50) DEFAULT NULL COMMENT '学号' AFTER `rname`,
ADD COLUMN `idcard` varchar(50) DEFAULT NULL COMMENT '身份证号' AFTER `number`;

-- 如果字段已存在，修改idcard字段长度
ALTER TABLE `user` 
MODIFY COLUMN `idcard` varchar(50) DEFAULT NULL COMMENT '身份证号';
