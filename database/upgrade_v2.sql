USE chatapp_db;

-- profile pictures
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar MEDIUMBLOB NULL AFTER salt;

-- voice notes
ALTER TABLE messages
  MODIFY msg_type ENUM('TEXT','IMAGE','DOCUMENT','VOICE') NOT NULL DEFAULT 'TEXT';
