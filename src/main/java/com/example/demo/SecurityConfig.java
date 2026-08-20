package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
                    .build();
        };
    }

        @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/dashboard/**").hasRole("ADMIN")
                .requestMatchers("/nurse/**").hasRole("NURSE")
                .requestMatchers("/doctor/**").hasRole("DOCTOR")
                .requestMatchers("/student/register").permitAll()
                .requestMatchers("/schedule/available").permitAll()
                .requestMatchers("/schedule/manage/**").hasAnyRole("DOCTOR", "ADMIN")
                .requestMatchers("/admin/users/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .httpBasic(basic -> {})
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
