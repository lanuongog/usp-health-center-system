package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserRepository userRepository;

    // FR-05-68, 69: view all accounts and their status
    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public static class StatusRequest {
        public Long userId;
    }

    // FR-05-70: deactivate an account
    @PostMapping("/deactivate")
    public Object deactivateUser(@RequestBody StatusRequest request) {
        User user = userRepository.findById(request.userId).orElse(null);
        if (user == null) {
            return Map.of("error", "User account not found.");
        }
        user.setActive(false);
        userRepository.save(user);
        return Map.of("message", "Account deactivated.", "username", user.getUsername());
    }

    // FR-05-70: reactivate an account
    @PostMapping("/activate")
    public Object activateUser(@RequestBody StatusRequest request) {
        User user = userRepository.findById(request.userId).orElse(null);
        if (user == null) {
            return Map.of("error", "User account not found.");
        }
        user.setActive(true);
        userRepository.save(user);
        return Map.of("message", "Account activated.", "username", user.getUsername());
    }
}