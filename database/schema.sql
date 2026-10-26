-- =====================================================================
--  HabitFlow - complete MySQL schema
--
--  HOW TO RUN (MySQL Workbench):
--    1. Open MySQL Workbench and connect to your local server.
--    2. File > Open SQL Script... and choose this file.
--    3. Click the lightning bolt (Execute) in the toolbar.
--    4. Refresh the SCHEMAS panel - habitflow_db appears with 8 tables.
--
--  This script is idempotent: running it twice does not fail.
--  Spring Boot also runs src/main/resources/db/schema.sql on start-up, so
--  creating the empty database is technically enough. Running this file
--  once makes the schema visible in Workbench for coursework screenshots.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS habitflow_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE habitflow_db;

-- ---------------------------------------------------------------------
-- 1. users - one row per account. Passwords are BCrypt hashes (60 chars).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL DEFAULT 'ROLE_USER',
    timezone      VARCHAR(60)  NOT NULL DEFAULT 'UTC',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 2. habit_categories - 8 global rows (owner_user_id NULL) seeded on start.
--    A user may also create private categories (owner_user_id = their id).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS habit_categories (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    name          VARCHAR(60) NOT NULL,
    color         VARCHAR(20) NOT NULL DEFAULT '#7C6BFF',
    icon          VARCHAR(40) NOT NULL DEFAULT 'bi-tag',
    owner_user_id BIGINT      NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_category_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_category_name_owner UNIQUE (name, owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. habits - owned by a user; frequency is DAILY / WEEKDAYS / WEEKLY / CUSTOM.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS habits (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    category_id   BIGINT        NOT NULL,
    name          VARCHAR(120)  NOT NULL,
    description   VARCHAR(500)  NULL,
    target_value  DECIMAL(10,2) NULL,
    target_unit   VARCHAR(30)   NULL,
    frequency     VARCHAR(20)   NOT NULL DEFAULT 'DAILY',
    weekly_target INT           NULL,
    color         VARCHAR(20)   NOT NULL DEFAULT '#7C6BFF',
    icon          VARCHAR(40)   NOT NULL DEFAULT 'bi-check2-circle',
    reminder_time TIME          NULL,
    start_date    DATE          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_habits_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_habits_category FOREIGN KEY (category_id) REFERENCES habit_categories (id),
    CONSTRAINT chk_habits_frequency CHECK (frequency IN ('DAILY','WEEKDAYS','WEEKLY','CUSTOM')),
    CONSTRAINT chk_habits_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    KEY idx_habits_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4. habit_schedules - which weekdays a habit is due.
--    1 = Monday ... 7 = Sunday (matches java.time.DayOfWeek.getValue()).
--    This table is the single source of truth for "scheduled".
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS habit_schedules (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    habit_id    BIGINT   NOT NULL,
    day_of_week TINYINT  NOT NULL COMMENT '1=Monday ... 7=Sunday',
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_habit FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE,
    CONSTRAINT uk_schedule_habit_day UNIQUE (habit_id, day_of_week),
    CONSTRAINT chk_schedule_day CHECK (day_of_week BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5. habit_completions - one row per habit per calendar day.
--    uk_completion_habit_date is the database-level guarantee against
--    duplicate completions (a racing double-click gets a 409, not a dup).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS habit_completions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    habit_id        BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    completion_date DATE         NOT NULL,
    note            VARCHAR(500) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_completion_habit FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE,
    CONSTRAINT fk_completion_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_completion_habit_date UNIQUE (habit_id, completion_date),
    KEY idx_completion_user_date (user_id, completion_date),
    KEY idx_completion_habit_date (habit_id, completion_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 6. goals - manual progress, or progress derived from linked habits.
--    milestone_reached remembers which milestone was last announced so a
--    25/50/75/100 message is only ever stored once per level.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS goals (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    title             VARCHAR(140) NOT NULL,
    description       VARCHAR(500) NULL,
    target_value      INT          NULL,
    manual_progress   INT          NOT NULL DEFAULT 0,
    milestone_reached INT          NOT NULL DEFAULT 0,
    target_date       DATE         NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at      TIMESTAMP    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_goals_status CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED')),
    KEY idx_goals_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 7. goal_habits - many-to-many join between goals and habits.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS goal_habits (
    goal_id    BIGINT    NOT NULL,
    habit_id   BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (goal_id, habit_id),
    CONSTRAINT fk_goal_habits_goal FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_habits_habit FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 8. notifications - in-app only (no email / SMS / web push).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(140) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    link       VARCHAR(200) NULL,
    is_read    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    KEY idx_notifications_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Verify
-- ---------------------------------------------------------------------
-- SHOW TABLES;
-- SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = 'habitflow_db';
