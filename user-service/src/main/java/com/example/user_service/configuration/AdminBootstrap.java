package com.example.user_service.configuration;

import com.example.user_service.entity.User;
import com.example.user_service.repository.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Registration can no longer create admins, so the first admin comes from configuration
// (ADMIN_EMAIL / ADMIN_PASSWORD). Does nothing if either is blank or the email is taken.
@Slf4j
@Component
public class AdminBootstrap implements CommandLineRunner {
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String email;

    @Value("${admin.password:}")
    private String password;

    @Override
    public void run(String... args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        if (userRepo.findByEmail(email).isPresent()) {
            // Never promote an existing account: whoever registered this email first may not be the admin.
            log.info("Admin bootstrap skipped: {} already exists", email);
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setName("Admin");
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        userRepo.save(admin);
        log.info("Created admin account {}", email);
    }
}
