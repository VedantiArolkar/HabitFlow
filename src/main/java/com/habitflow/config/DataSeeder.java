package com.habitflow.config;

import com.habitflow.model.Habit;
import com.habitflow.model.HabitCategory;
import com.habitflow.repository.CategoryRepository;
import com.habitflow.repository.CompletionRepository;
import com.habitflow.repository.GoalRepository;
import com.habitflow.repository.HabitRepository;
import com.habitflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Seeds the eight global habit categories on every start (idempotent), plus - only when
 * app.seed-demo-data=true - one demo account with 6 habits and ~60 days of history so the
 * dashboard and charts are not empty on first run. Disable with HABITFLOW_SEED_DEMO=false.
 *
 * Demo credentials come from application.properties and are overridable by environment
 * variables; they are a convenience for local development only, never a back door.
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[][] CATEGORIES = {
            {"Health", "#14B8A6", "bi-heart-pulse"},
            {"Fitness", "#F97316", "bi-lightning-charge"},
            {"Learning", "#6366F1", "bi-book"},
            {"Career", "#0EA5E9", "bi-briefcase"},
            {"Productivity", "#8B5CF6", "bi-clock"},
            {"Mindfulness", "#10B981", "bi-flower1"},
            {"Personal Development", "#EC4899", "bi-stars"},
            {"Other", "#64748B", "bi-tag"}
    };

    @Value("${app.seed-demo-data:true}")
    private boolean seedDemoData;

    @Value("${app.demo-email:demo@habitflow.app}")
    private String demoEmail;

    @Value("${app.demo-password:Demo@12345}")
    private String demoPassword;

    @Bean
    @Transactional
    public ApplicationRunner seedRunner(UserRepository users, CategoryRepository categories,
                                        HabitRepository habits, CompletionRepository completions,
                                        GoalRepository goals, PasswordEncoder encoder) {
        return (ApplicationArguments args) -> {
            seedCategories(categories);
            if (!seedDemoData) {
                log.info("Demo seeding disabled (app.seed-demo-data=false).");
                return;
            }
            if (users.existsByEmail(demoEmail)) {
                log.info("Demo account {} already present - skipping seed.", demoEmail);
                return;
            }
            seedDemoUser(users, categories, habits, completions, goals, encoder);
            log.info("Demo data created. Sign in with {} / {}", demoEmail, demoPassword);
        };
    }

    private void seedCategories(CategoryRepository categories) {
        for (String[] c : CATEGORIES) {
            boolean exists = categories.findVisibleTo(0L).stream()
                    .anyMatch(x -> x.name().equalsIgnoreCase(c[0]));
            if (!exists) {
                categories.insert(c[0], c[1], c[2], null);
            }
        }
    }

    private void seedDemoUser(UserRepository users, CategoryRepository categories, HabitRepository habits,
                              CompletionRepository completions, GoalRepository goals, PasswordEncoder encoder) {
        Long userId = users.insert("Alex Morgan", demoEmail.toLowerCase(), encoder.encode(demoPassword), "UTC");
        List<HabitCategory> cats = categories.findVisibleTo(userId);
        Long health = idOf(cats, "Health");
        Long fitness = idOf(cats, "Fitness");
        Long learning = idOf(cats, "Learning");
        Long productivity = idOf(cats, "Productivity");
        Long mindfulness = idOf(cats, "Mindfulness");

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(59);

        // name, category, icon, color, frequency, days, reminder, target, unit, description, successRate
        Object[][] spec = {
                {"Drink 2L of water", health, "bi-droplet", "#14B8A6", "DAILY", allDays(), "08:00",
                        new BigDecimal("2"), "litres", "Hydration first - it makes everything else easier.", 0.90},
                {"Exercise 30 minutes", fitness, "bi-activity", "#F97316", "CUSTOM", days(1, 2, 3, 4, 5),
                        "18:30", null, null, "Any movement counts: run, gym, or a brisk walk.", 0.72},
                {"Read for 20 minutes", learning, "bi-book", "#6366F1", "DAILY", allDays(), "21:00",
                        new BigDecimal("20"), "minutes", "Fiction or non-fiction - consistency beats volume.", 0.80},
                {"Practice Java for 45 min", learning, "bi-code-slash", "#8B5CF6", "CUSTOM",
                        days(1, 2, 3, 4, 5, 6), "19:30", new BigDecimal("45"), "minutes",
                        "Spring Boot, JDBC and DSA - the internship prep habit.", 0.83},
                {"Meditate 10 minutes", mindfulness, "bi-flower1", "#10B981", "DAILY", allDays(), "07:00",
                        new BigDecimal("10"), "minutes", "Ten quiet minutes before the day starts.", 0.60},
                {"Plan tomorrow before bed", productivity, "bi-journal-check", "#0EA5E9", "DAILY", allDays(),
                        "22:00", null, null, "Three priorities for tomorrow, written down.", 0.76}
        };

        List<Long> habitIds = new java.util.ArrayList<>();
        Random random = new Random(42);
        for (Object[] row : spec) {
            @SuppressWarnings("unchecked")
            Set<Integer> schedule = (Set<Integer>) row[5];
            double rate = (double) row[11];
            Long habitId = habits.insert(userId, (Long) row[1], (String) row[0], (String) row[9],
                    (BigDecimal) row[7], (String) row[8], (String) row[4], null,
                    (String) row[3], (String) row[2], LocalTime.parse((String) row[6]), start, "ACTIVE");
            habits.replaceSchedule(habitId, schedule);
            habitIds.add(habitId);

            // Generate realistic history: mostly completed, with a few missed days and a
            // recent run of successes so streaks look believable.
            int dayIndex = 0;
            for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1), dayIndex++) {
                if (!schedule.contains(d.getDayOfWeek().getValue())) {
                    continue;
                }
                boolean recentBoost = dayIndex > 50;
                double chance = recentBoost ? Math_min(0.98, rate + 0.12) : rate;
                if (random.nextDouble() < chance) {
                    completions.insert(habitId, userId, d, null);
                }
            }
        }

        Long goalId = goals.insert(userId, "Build a daily Java practice routine",
                "Complete 40 Java practice sessions to be interview-ready.", 40, 0,
                today.plusDays(30), "ACTIVE");
        goals.linkHabits(goalId, List.of(habitIds.get(3)));

        Long goalTwo = goals.insert(userId, "Run 20 workouts this quarter",
                "Consistency over intensity - twenty sessions of any kind of movement.", 20, 0,
                today.plusDays(60), "ACTIVE");
        goals.linkHabits(goalTwo, List.of(habitIds.get(1)));
    }

    private static double Math_min(double a, double b) {
        return Math.min(a, b);
    }

    private static Set<Integer> allDays() {
        return days(1, 2, 3, 4, 5, 6, 7);
    }

    private static Set<Integer> days(int... values) {
        Set<Integer> set = new LinkedHashSet<>();
        for (int v : values) {
            set.add(v);
        }
        return set;
    }

    private Long idOf(List<HabitCategory> cats, String name) {
        return cats.stream().filter(c -> c.name().equalsIgnoreCase(name))
                .map(HabitCategory::id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing seeded category " + name));
    }

    /** Kept for readability of the Habit import in tooling/IDE inspections. */
    @SuppressWarnings("unused")
    private void touchModel(Habit habit) {
    }
}
