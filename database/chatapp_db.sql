CREATE DATABASE IF NOT EXISTS chatapp_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE chatapp_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS login_logs;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS files;
DROP TABLE IF EXISTS conversation_members;
DROP TABLE IF EXISTS conversations;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;


CREATE TABLE users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(30)  NOT NULL,
  full_name     VARCHAR(100) NOT NULL,
  email         VARCHAR(120) NOT NULL,
  password_hash VARCHAR(64)  NOT NULL,
  salt          VARCHAR(32)  NOT NULL,
  avatar        MEDIUMBLOB   NULL,          -- profile picture (192x192 PNG), NULL = none
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login    TIMESTAMP    NULL DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;


CREATE TABLE conversations (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  type       ENUM('PRIVATE','GROUP') NOT NULL,
  name       VARCHAR(60) NULL,
  created_by BIGINT      NOT NULL,
  created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_conv_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE conversation_members (
  id              BIGINT    NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT    NOT NULL,
  user_id         BIGINT    NOT NULL,
  role            ENUM('ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
  joined_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_member (conversation_id, user_id),
  CONSTRAINT fk_cm_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_cm_user FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE
) ENGINE=InnoDB;


CREATE TABLE files (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  uploader_id   BIGINT       NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  stored_name   VARCHAR(100) NOT NULL,
  mime_type     VARCHAR(100) NOT NULL,
  size_bytes    BIGINT       NOT NULL,
  uploaded_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_files_stored (stored_name),
  CONSTRAINT fk_files_user FOREIGN KEY (uploader_id) REFERENCES users(id)
) ENGINE=InnoDB;


CREATE TABLE messages (
  id              BIGINT    NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT    NOT NULL,
  sender_id       BIGINT    NOT NULL,
  msg_type        ENUM('TEXT','IMAGE','DOCUMENT','VOICE') NOT NULL DEFAULT 'TEXT',
  content         TEXT      NULL,
  file_id         BIGINT    NULL,
  sent_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_msg_conv_time (conversation_id, sent_at),
  CONSTRAINT fk_msg_conv   FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id)       REFERENCES users(id),
  CONSTRAINT fk_msg_file   FOREIGN KEY (file_id)         REFERENCES files(id) ON DELETE SET NULL
) ENGINE=InnoDB;


CREATE TABLE login_logs (
  id                 BIGINT      NOT NULL AUTO_INCREMENT,
  user_id            BIGINT      NULL,
  username_attempted VARCHAR(30) NOT NULL,
  success            TINYINT(1)  NOT NULL,
  ip_address         VARCHAR(45) NULL,
  attempted_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_login_user_time (username_attempted, attempted_at),
  CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;
