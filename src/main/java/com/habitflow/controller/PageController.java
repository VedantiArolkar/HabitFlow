package com.habitflow.controller;

import com.habitflow.security.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Thymeleaf page shells. All data is loaded by JavaScript from the /api/**
 * endpoints, so a page render is a single fast template call with no database work.
 */
@Controller
public class PageController {

    private final CurrentUser currentUser;

    public PageController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping({"/", "/features"})
    public String landing() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        return authedPage(model, "dashboard", "Dashboard", "Overview", "dashboard");
    }

    @GetMapping("/habits")
    public String habits(Model model) {
        return authedPage(model, "habits", "My Habits", "Habits", "habits");
    }

    @GetMapping("/tracking")
    public String tracking(Model model) {
        return authedPage(model, "tracking", "Daily Tracking", "Tracking", "tracking");
    }

    @GetMapping("/goals")
    public String goals(Model model) {
        return authedPage(model, "goals", "Goals & Milestones", "Goals", "goals");
    }

    @GetMapping("/calendar")
    public String calendar(Model model) {
        return authedPage(model, "calendar", "Calendar & History", "Calendar", "calendar");
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        return authedPage(model, "analytics", "Analytics & Reports", "Analytics", "analytics");
    }

    @GetMapping("/insights")
    public String insights(Model model) {
        return authedPage(model, "insights", "Smart Insights", "Insights", "insights");
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        return authedPage(model, "notifications", "Notifications", "Notifications", "notifications");
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        return authedPage(model, "settings", "Profile & Settings", "Settings", "settings");
    }

    private String authedPage(Model model, String view, String title, String subtitle, String active) {
        model.addAttribute("pageTitle", title);
        model.addAttribute("pageSubtitle", subtitle);
        model.addAttribute("activePage", active);
        try {
            model.addAttribute("currentUserName", currentUser.principal().getFullName());
            model.addAttribute("currentUserEmail", currentUser.principal().getEmail());
        } catch (RuntimeException ex) {
            model.addAttribute("currentUserName", "Guest");
            model.addAttribute("currentUserEmail", "");
        }
        return view;
    }
}
