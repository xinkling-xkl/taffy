-- 添加message字段到job_application表，用于存储商户留言或拒绝原因
ALTER TABLE job_application ADD COLUMN message TEXT COMMENT '商户留言或拒绝原因';
