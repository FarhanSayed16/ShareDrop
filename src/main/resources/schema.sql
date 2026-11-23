-- ClassDrop Database Schema (MySQL 8)
-- Farhan Sayed - KJSIM MCA

CREATE DATABASE IF NOT EXISTS classdrop;
USE classdrop;

CREATE TABLE IF NOT EXISTS sessions (
  id           CHAR(36)     NOT NULL,
  room_code    CHAR(6)      NOT NULL,
  started_at   DATETIME     NOT NULL,
  ended_at     DATETIME     NULL,
  mode         VARCHAR(20)  NOT NULL,
  host_ip      VARCHAR(45)  NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_sessions_started (started_at)
);

CREATE TABLE IF NOT EXISTS devices (
  id            CHAR(36)     NOT NULL,
  session_id    CHAR(36)     NOT NULL,
  display_name  VARCHAR(80)  NOT NULL,
  ip_address    VARCHAR(45)  NULL,
  joined_at     DATETIME     NOT NULL,
  last_seen     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_devices_session
    FOREIGN KEY (session_id) REFERENCES sessions(id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transfers (
  id             CHAR(36)      NOT NULL,
  session_id     CHAR(36)      NOT NULL,
  device_id      CHAR(36)      NOT NULL,
  item_kind      VARCHAR(20)   NOT NULL,
  original_name  VARCHAR(255)  NOT NULL,
  stored_name    VARCHAR(80)   NOT NULL,
  size_bytes     BIGINT        NOT NULL,
  content_type   VARCHAR(120)  NULL,
  direction      VARCHAR(10)   NOT NULL,
  status         VARCHAR(20)   NOT NULL,
  file_token     CHAR(36)      NULL,
  created_at     DATETIME      NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_transfers_search (original_name, item_kind, created_at),
  CONSTRAINT fk_transfers_session
    FOREIGN KEY (session_id) REFERENCES sessions(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_transfers_device
    FOREIGN KEY (device_id) REFERENCES devices(id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS snippets (
  id          CHAR(36)      NOT NULL,
  session_id  CHAR(36)      NOT NULL,
  device_id   CHAR(36)      NOT NULL,
  title       VARCHAR(120)  NULL,
  language    VARCHAR(40)   NULL,
  body        TEXT          NOT NULL,
  created_at  DATETIME      NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_snippets_session
    FOREIGN KEY (session_id) REFERENCES sessions(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_snippets_device
    FOREIGN KEY (device_id) REFERENCES devices(id)
    ON DELETE CASCADE
);
