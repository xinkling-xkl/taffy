-- 创建job表
CREATE TABLE IF NOT EXISTS job (
    id INT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    address VARCHAR(255) NULL,
    phone VARCHAR(20) NULL,
    image_url VARCHAR(255) NULL,
    user_id INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT '进行中',
    create_time DATETIME NOT NULL,
    salary VARCHAR(100) NULL,
    time VARCHAR(200) NULL,
    category VARCHAR(50) NULL
);

-- 添加索引
CREATE INDEX idx_user_id ON job(user_id);
CREATE INDEX idx_status ON job(status);
CREATE INDEX idx_create_time ON job(create_time);