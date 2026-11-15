-- =====================================================================
--  UPGRADE for people who already imported the FIRST version of chatapp_db.sql.
--  Keeps all existing users, chats and messages.
--  phpMyAdmin -> select chatapp_db -> SQL tab -> paste -> Go.
--  (New installs do not need this: the full chatapp_db.sql already includes it.)
-- =====================================================================
USE chatapp_db;

-- profile pictures
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar MEDIUMBLOB NULL AFTER salt;

-- voice notes
ALTER TABLE messages
  MODIFY msg_type ENUM('TEXT','IMAGE','DOCUMENT','VOICE') NOT NULL DEFAULT 'TEXT';
