-- =====================================================================
--  HabitFlow - optional development sample data (MySQL)
--
--  You usually do NOT need this file: the application seeds the 8 global
--  categories on every start, and with app.seed-demo-data=true it also
--  creates the demo account with 6 habits, ~9 weeks of completions and
--  2 goals through real repository code.
--
--  Use this file only when you want SQL-only sample data, e.g. to show
--  MySQL Workbench queries in a report without starting the app yet.
--  It inserts the demo user with an already-hashed BCrypt password.
-- =====================================================================

USE habitflow_db;

-- Demo password (plain text): Demo@12345
-- The value below is a BCrypt hash of that password.
INSERT INTO users (full_name, email, password_hash, role, timezone)
VALUES ('Demo User', 'demo@habitflow.app',
        '$2a$12$Q9Zt5v2m3T1kR8yWq7Yb2eJ0o3Xk9dQm6rP1sV4nL8cU2hB5gA1Ie',
        'ROLE_USER', 'UTC')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

SET @demo_id = (SELECT id FROM users WHERE email = 'demo@habitflow.app');

-- Global categories (skipped if the app already seeded them)
INSERT INTO habit_categories (name, color, icon, owner_user_id) VALUES
  ('Health', '#14B8A6', 'bi-heart-pulse', NULL),
  ('Fitness', '#F97316', 'bi-lightning-charge', NULL),
  ('Learning', '#6366F1', 'bi-book', NULL),
  ('Career', '#0EA5E9', 'bi-briefcase', NULL),
  ('Productivity', '#8B5CF6', 'bi-clock', NULL),
  ('Mindfulness', '#10B981', 'bi-flower1', NULL),
  ('Personal Development', '#EC4899', 'bi-stars', NULL),
  ('Other', '#64748B', 'bi-tag', NULL)
ON DUPLICATE KEY UPDATE color = VALUES(color);

SET @health = (SELECT id FROM habit_categories WHERE name = 'Health' AND owner_user_id IS NULL);
SET @fitness = (SELECT id FROM habit_categories WHERE name = 'Fitness' AND owner_user_id IS NULL);
SET @learning = (SELECT id FROM habit_categories WHERE name = 'Learning' AND owner_user_id IS NULL);

-- Example habits
INSERT INTO habits (user_id, category_id, name, description, target_value, target_unit,
                    frequency, color, icon, reminder_time, start_date, status)
VALUES
  (@demo_id, @health,   'Drink 2L of water', 'Keep a bottle on the desk and refill after lunch.',
   2.00, 'litres', 'DAILY', '#14B8A6', 'bi-droplet', '08:00:00', DATE_SUB(CURDATE(), INTERVAL 60 DAY), 'ACTIVE'),
  (@demo_id, @fitness,  'Exercise 30 minutes', 'Walk, gym or a short home workout.',
   30.00, 'minutes', 'CUSTOM', '#F97316', 'bi-activity', '18:30:00', DATE_SUB(CURDATE(), INTERVAL 60 DAY), 'ACTIVE'),
  (@demo_id, @learning, 'Practice Java for 45 min', 'One topic per session, then commit the code.',
   45.00, 'minutes', 'CUSTOM', '#6366F1', 'bi-code-slash', '19:30:00', DATE_SUB(CURDATE(), INTERVAL 60 DAY), 'ACTIVE');

-- Schedules (Mon-Fri for the exercise habit, Mon-Sat for Java practice)
INSERT INTO habit_schedules (habit_id, day_of_week)
SELECT h.id, d.n
FROM habits h
JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) d
WHERE h.user_id = @demo_id AND h.name = 'Exercise 30 minutes'
ON DUPLICATE KEY UPDATE day_of_week = VALUES(day_of_week);

INSERT INTO habit_schedules (habit_id, day_of_week)
SELECT h.id, d.n
FROM habits h
JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) d
WHERE h.user_id = @demo_id AND h.name = 'Practice Java for 45 min'
ON DUPLICATE KEY UPDATE day_of_week = VALUES(day_of_week);

INSERT INTO habit_schedules (habit_id, day_of_week)
SELECT h.id, d.n
FROM habits h
JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
      UNION SELECT 6 UNION SELECT 7) d
WHERE h.user_id = @demo_id AND h.name = 'Drink 2L of water'
ON DUPLICATE KEY UPDATE day_of_week = VALUES(day_of_week);

-- A few completions so charts are not empty
INSERT INTO habit_completions (habit_id, user_id, completion_date, note)
SELECT h.id, @demo_id, DATE_SUB(CURDATE(), INTERVAL d.n DAY), NULL
FROM habits h
JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 5 UNION SELECT 6) d
WHERE h.user_id = @demo_id AND h.name = 'Drink 2L of water'
ON DUPLICATE KEY UPDATE note = VALUES(note);

-- One goal linked to the Java habit
INSERT INTO goals (user_id, title, description, target_value, manual_progress, target_date, status)
VALUES (@demo_id, 'Build a daily Java practice routine',
        'Forty focused sessions before the internship interviews.',
        40, 0, DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'ACTIVE')
ON DUPLICATE KEY UPDATE title = VALUES(title);

SET @goal_id = (SELECT id FROM goals WHERE user_id = @demo_id
                AND title = 'Build a daily Java practice routine' LIMIT 1);

INSERT INTO goal_habits (goal_id, habit_id)
SELECT @goal_id, h.id FROM habits h
WHERE h.user_id = @demo_id AND h.name = 'Practice Java for 45 min'
ON DUPLICATE KEY UPDATE goal_id = VALUES(goal_id);

-- Check it worked
-- SELECT id, full_name, email, created_at FROM users;
-- SELECT h.name, h.frequency, COUNT(c.id) AS completions
--   FROM habits h LEFT JOIN habit_completions c ON c.habit_id = h.id
--   WHERE h.user_id = @demo_id GROUP BY h.id;
