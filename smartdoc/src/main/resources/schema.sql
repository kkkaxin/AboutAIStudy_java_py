    -- SmartDoc 数据库初始化脚本
    -- 执行前请先创建数据库：CREATE DATABASE smartdoc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

    -- 用户表
    CREATE TABLE IF NOT EXISTS `sd_user` (
        `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
        `username`    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
        `password`    VARCHAR(100) NOT NULL COMMENT '加密密码',
        `email`       VARCHAR(100) COMMENT '邮箱',
        `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
        `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

    -- 知识库表
    CREATE TABLE IF NOT EXISTS `sd_knowledge_base` (
        `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
        `name`        VARCHAR(100) NOT NULL COMMENT '知识库名称',
        `description` TEXT         COMMENT '知识库描述',
        `user_id`     BIGINT       NOT NULL COMMENT '创建者ID',
        `doc_count`   INT          NOT NULL DEFAULT 0 COMMENT '文档数量',
        `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (`id`),
        KEY `idx_user_id` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

    -- 文档表
    CREATE TABLE IF NOT EXISTS `sd_document` (
        `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
        `kb_id`        BIGINT       NOT NULL COMMENT '所属知识库ID',
        `file_name`    VARCHAR(255) NOT NULL COMMENT '原始文件名',
        `file_path`    VARCHAR(500) NOT NULL COMMENT '存储路径',
        `file_type`    VARCHAR(20)  NOT NULL COMMENT '文件类型：pdf/txt/md',
        `file_size`    BIGINT       NOT NULL COMMENT '文件大小（字节）',
        `chunk_count`  INT          NOT NULL DEFAULT 0 COMMENT '切分块数',
        `status`       VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/DONE/FAILED',
        `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (`id`),
        KEY `idx_kb_id` (`kb_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

    -- 会话表
    CREATE TABLE IF NOT EXISTS `sd_conversation` (
        `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
        `title`       VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
        `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
        `kb_id`       BIGINT       COMMENT '关联知识库ID（为空则纯对话模式）',
        `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (`id`),
        KEY `idx_user_id` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

    -- 消息表
    CREATE TABLE IF NOT EXISTS `sd_message` (
        `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
        `conversation_id` BIGINT       NOT NULL COMMENT '所属会话ID',
        `role`            VARCHAR(20)  NOT NULL COMMENT '角色：USER/ASSISTANT',
        `content`         LONGTEXT     NOT NULL COMMENT '消息内容',
        `sources`         JSON         COMMENT '引用来源（RAG 检索到的文档片段）',
        `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (`id`),
        KEY `idx_conversation_id` (`conversation_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

    -- 插入默认管理员账号（密码：admin123，BCrypt加密）
    INSERT INTO `sd_user` (`username`, `password`, `email`, `role`)
    VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iANxXCk.HpOAkFOFwBrlWxmfDklu', 'admin@smartdoc.com', 'ADMIN')
    ON DUPLICATE KEY UPDATE id=id;
