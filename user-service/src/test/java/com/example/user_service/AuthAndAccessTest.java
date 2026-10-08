package com.example.user_service;

import com.example.user_service.configuration.AdminBootstrap;
import com.example.user_service.controller.UserController;
import com.example.user_service.dto.AuthReq;
import com.example.user_service.dto.UserDto;
import com.example.user_service.entity.User;
import com.example.user_service.exception.NotFoundException;
import com.example.user_service.repository.UserRepo;
import com.example.user_service.service.AuthService;
import com.example.user_service.service.LoginAttemptService;
import com.example.user_service.exception.TooManyRequestsException;
import com.example.user_service.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthAndAccessTest {
    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4); // low cost keeps tests fast

    private final UserRepo userRepo = mock(UserRepo.class);

    @BeforeEach
    void saveReturnsItsArgument() {
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(42L);
            }
            return u;
        });
    }

    @Nested
    class Registration {
        private final AuthService authService = authService();

        @Test
        void requestedAdminRoleIsIgnored() {
            authService.register(req("Mallory", "mallory@test.local", "Passw0rd!", "ADMIN"));

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepo).save(saved.capture());
            assertThat(saved.getValue().getRole()).isEqualTo("USER");
        }

        @Test
        void passwordIsStoredHashed() {
            authService.register(req("Asha", "asha@test.local", "Passw0rd!", null));

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepo).save(saved.capture());
            assertThat(saved.getValue().getPassword()).isNotEqualTo("Passw0rd!");
            assertThat(ENCODER.matches("Passw0rd!", saved.getValue().getPassword())).isTrue();
        }

        @Test
        void duplicateEmailIsAConflict() {
            when(userRepo.findByEmail("asha@test.local")).thenReturn(Optional.of(new User()));

            assertThatThrownBy(() -> authService.register(req("Asha", "asha@test.local", "Passw0rd!", null)))
                    .isInstanceOf(IllegalStateException.class);
            verify(userRepo, never()).save(any());
        }

        @Test
        void shortPasswordAndMissingNameAreRejected() {
            assertThatThrownBy(() -> authService.register(req("Asha", "a@test.local", "short", null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> authService.register(req(" ", "a@test.local", "Passw0rd!", null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Login {
        private final AuthService authService = authService();

        @Test
        void wrongPasswordAndUnknownEmailFailTheSameWay() {
            when(userRepo.findByEmail("asha@test.local")).thenReturn(Optional.of(user(1L, "USER", "Passw0rd!")));

            assertThatThrownBy(() -> authService.login(req(null, "asha@test.local", "WrongPass1", null)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
            assertThatThrownBy(() -> authService.login(req(null, "nobody@test.local", "Passw0rd!", null)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        void afterFiveWrongPasswordsEvenTheRightOneIsRefusedForAWhile() {
            when(userRepo.findByEmail("asha@test.local")).thenReturn(Optional.of(user(1L, "USER", "Passw0rd!")));
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> authService.login(req(null, "asha@test.local", "Guess" + System.nanoTime(), null)))
                        .isInstanceOf(BadCredentialsException.class);
            }

            // Otherwise a guesser could tell from the response which guess was right.
            assertThatThrownBy(() -> authService.login(req(null, "asha@test.local", "Passw0rd!", null)))
                    .isInstanceOf(TooManyRequestsException.class);
        }

        @Test
        void issuedTokenIsAcceptedByTheDecoderTheOtherServicesUse() {
            String token = authService.issueToken(user(7L, "ADMIN", "x"));

            // Same decoder setup as every service's SecurityConfig.
            Jwt jwt = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .build().decode(token);
            assertThat(jwt.getSubject()).isEqualTo("7"); // payment and user ownership checks rely on this
            assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
            assertThat(jwt.getClaimAsString("email")).isEqualTo("user7@test.local");
        }
    }

    @Nested
    class Bootstrap {
        @Test
        void createsTheConfiguredAdmin() {
            bootstrap("admin@test.local", "AdminPass123").run();

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepo).save(saved.capture());
            assertThat(saved.getValue().getRole()).isEqualTo("ADMIN");
            assertThat(ENCODER.matches("AdminPass123", saved.getValue().getPassword())).isTrue();
        }

        @Test
        void neverPromotesAnExistingAccount() {
            when(userRepo.findByEmail("admin@test.local")).thenReturn(Optional.of(user(3L, "USER", "x")));

            bootstrap("admin@test.local", "AdminPass123").run();

            verify(userRepo, never()).save(any());
        }

        @Test
        void doesNothingWhenNotConfigured() {
            bootstrap("", "").run();
            bootstrap("admin@test.local", "").run();

            verify(userRepo, never()).findByEmail(anyString());
            verify(userRepo, never()).save(any());
        }

        private AdminBootstrap bootstrap(String email, String password) {
            AdminBootstrap b = new AdminBootstrap();
            ReflectionTestUtils.setField(b, "userRepo", userRepo);
            ReflectionTestUtils.setField(b, "passwordEncoder", ENCODER);
            ReflectionTestUtils.setField(b, "email", email);
            ReflectionTestUtils.setField(b, "password", password);
            return b;
        }
    }

    @Nested
    class ProfileAccess {
        private final UserService userService = mock(UserService.class);
        private final UserController controller = new UserController();

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(controller, "userService", userService);
            UserDto dto = new UserDto();
            dto.setId(5L);
            when(userService.getById(5L)).thenReturn(dto);
        }

        @Test
        void usersCanReadTheirOwnProfile() {
            assertThat(controller.getById(5L, jwt("5", "USER")).getBody().getData().getId()).isEqualTo(5L);
        }

        @Test
        void usersCannotReadSomeoneElsesProfile() {
            assertThatThrownBy(() -> controller.getById(5L, jwt("6", "USER")))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found");
            verify(userService, never()).getById(any());
        }

        @Test
        void adminsCanReadAnyProfile() {
            assertThat(controller.getById(5L, jwt("1", "ADMIN")).getStatusCode().value()).isEqualTo(200);
        }

        private Jwt jwt(String subject, String role) {
            return Jwt.withTokenValue("t").header("alg", "HS256").subject(subject).claim("role", role).build();
        }
    }

    private AuthService authService() {
        AuthService s = new AuthService();
        ReflectionTestUtils.setField(s, "userRepo", userRepo);
        ReflectionTestUtils.setField(s, "passwordEncoder", ENCODER);
        ReflectionTestUtils.setField(s, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(s, "loginAttempts", new LoginAttemptService(5, java.time.Duration.ofMinutes(15)));
        return s;
    }

    private static AuthReq req(String name, String email, String password, String role) {
        return new AuthReq(name, email, password, role);
    }

    private static User user(Long id, String role, String rawPassword) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.local");
        u.setName("User " + id);
        u.setRole(role);
        u.setPassword(ENCODER.encode(rawPassword));
        return u;
    }
}
