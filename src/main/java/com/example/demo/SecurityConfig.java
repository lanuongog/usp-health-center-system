package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .roles(user.getRole())
                    .disabled(!user.isActive())
                    .build();
        };
    }

    // when an already-authenticated user with the wrong role
    // hits a protected endpoint directly (not via the login form), return the
    // specific message instead of Spring Security's generic 403 body.
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.access.AccessDeniedException ex) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            String path = request.getRequestURI();
            String message = path.startsWith("/admin")
                    ? "You do not have permission to manage user accounts"
                    : "You do not have permission to view this page";
            response.getWriter().write("{\"message\": \"" + message + "\"}");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/dashboard/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/nurse/**").hasAnyRole("NURSE", "ADMIN")
                .requestMatchers("/doctor/**").hasRole("DOCTOR")
                .requestMatchers("/student/register", "/student/fee-schedule").permitAll()
                .requestMatchers("/student/**").hasRole("STUDENT")
                .requestMatchers("/schedule/available").permitAll()
                .requestMatchers("/schedule/manage/**").hasAnyRole("DOCTOR", "ADMIN")
                .requestMatchers("/directory/**").hasAnyRole("NURSE", "DOCTOR", "ADMIN")
                .requestMatchers("/absences/**").hasAnyRole("NURSE", "ADMIN")
                .requestMatchers("/notifications/**").hasAnyRole("NURSE", "DOCTOR", "ADMIN")
                .requestMatchers("/emergency/**").hasAnyRole("NURSE", "DOCTOR", "ADMIN")
                .requestMatchers("/account/**").authenticated()
                .anyRequest().permitAll()
            )
            .httpBasic(basic -> {})
            .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
