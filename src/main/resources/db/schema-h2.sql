-- Same tables/columns as db/schema.sql, expressed for H2 (MySQL compatibility mode).
-- Used only by the "test" profile so unit/integration tests never need a MySQL server.
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER',
    timezone VARCHAR(60) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS habit_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    color VARCHAR(20) NOT NULL DEFAULT '#7C6BFF',
    icon VARCHAR(40) NOT NULL DEFAULT 'bi-tag',
    owner_user_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_category_name_owner UNIQUE (name, owner_user_id)
);

CREATE TABLE IF NOT EXISTS habits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    target_value DECIMAL(10,2) NULL,
    target_unit VARCHAR(30) NULL,
    frequency VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    weekly_target INT NULL,
    color VARCHAR(20) NOT NULL DEFAULT '#7C6BFF',
    icon VARCHAR(40) NOT NULL DEFAULT 'bi-check2-circle',
    reminder_time TIME NULL,
    start_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_habits_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_habits_category FOREIGN KEY (category_id) REFERENCES habit_categories (id),
    CONSTRAINT chk_habits_frequency CHECK (frequency IN ('DAILY','WEEKDAYS','WEEKLY','CUSTOM')),
    CONSTRAINT chk_habits_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_habits_user_status ON habits (user_id, status);

CREATE TABLE IF NOT EXISTS habit_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    habit_id BIGINT NOT NULL,
    day_of_week TINYINT NOT NULL,
    CONSTRAINT fk_schedule_habit FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE,
    CONSTRAINT uk_schedule_habit_day UNIQUE (habit_id, day_of_week),
    CONSTRAINT chk_schedule_day CHECK (day_of_week BETWEEN 1 AND 7)
);

CREATE TABLE IF NOT EXISTS habit_completions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    habit_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    completion_date DATE NOT NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_completion_habit FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE,
    CONSTRAINT fk_completion_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_completion_habit_date UNIQUE (habit_id, completion_date)
);
CREATE INDEX IF NOT EXISTS idx_completion_user_date ON habit_completions (user_id, completion_date);
CREATE INDEX IF NOT EXISTS idx_completion_habit_date ON habit_completions (habit_id, completion_date);

CREATE TABLE IF NOT EXISTS goals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(140) NOT NULL,
    description VARCHAR(500) NULL,
    target_value INT NULL,
    manual_progress INT NOT NULL DEFAULT 0,
    milestone_reached INT NOT NULL DEFAULT 0,
    target_date DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_goals_status CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_goals_user_status ON goals (user_id, status);

CREATE TABLE IF NOT EXISTS goal_habits (
    goal_id BIGINT NOT NULL,
    habit_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (goal_id, habit_id),
    CONSTRAINT fk_goal_habits_goal FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_habits_habit FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(140) NOT NULL,
    message VARCHAR(500) NOT NULL,
    link VARCHAR(200) NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notifications (user_id, is_read);
