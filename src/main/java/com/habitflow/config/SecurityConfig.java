package com.habitflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitflow.security.AppUserDetailsService;
import com.habitflow.security.JsonAuthHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AppUserDetailsService userDetailsService, ObjectMapper objectMapper) {
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 12: modern, salted, deliberately slow.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    /** Exposed as a bean so AuthController can hand the SPA its CSRF token. */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookiePath("/");
        return repo;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // SPA-style JS reads the CSRF token from the XSRF-TOKEN cookie and sends it
        // back in the X-XSRF-TOKEN header. Double-submit pattern, no token leakage.
        CookieCsrfTokenRepository csrfRepo = csrfTokenRepository();
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        JsonAuthHandlers jsonAuth = new JsonAuthHandlers(objectMapper);

        http
            .authenticationProvider(authenticationProvider())
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfRepo)
                    .csrfTokenRequestHandler(requestHandler)
                    .ignoringRequestMatchers(new AntPathRequestMatcher("/api/auth/register")))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/login", "/register", "/features",
                            "/css/**", "/js/**", "/img/**", "/favicon.ico", "/error").permitAll()
                    .requestMatchers("/api/auth/register", "/api/auth/login", "/api/health").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated())
            .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/api/auth/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .successHandler((request, response, authentication) -> {
                        response.setStatus(200);
                        response.setContentType("application/json");
                        objectMapper.writeValue(response.getOutputStream(),
                                java.util.Map.of("success", true, "message", "Welcome back!"));
                    })
                    .failureHandler((request, response, exception) -> {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        objectMapper.writeValue(response.getOutputStream(),
                                java.util.Map.of("success", false,
                                        "message", "Incorrect email or password."));
                    })
                    .permitAll())
            .logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((request, response, authentication) -> {
                        response.setStatus(200);
                        response.setContentType("application/json");
                        objectMapper.writeValue(response.getOutputStream(),
                                java.util.Map.of("success", true, "message", "Signed out."));
                    })
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", "XSRF-TOKEN"))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, e) -> {
                        if (request.getRequestURI().startsWith("/api/")) {
                            jsonAuth.commence(request, response, e);
                        } else {
                            response.sendRedirect("/login");
                        }
                    })
                    .accessDeniedHandler(jsonAuth))
            .sessionManagement(session -> session
                    .sessionFixation().migrateSession()
                    .maximumSessions(5));

        return http.build();
    }
}
