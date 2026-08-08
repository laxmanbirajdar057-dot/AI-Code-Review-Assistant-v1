package com.laxman.codereviewassistant.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.laxman.codereviewassistant.dto.AuthResponse;
import com.laxman.codereviewassistant.dto.LoginRequest;
import com.laxman.codereviewassistant.dto.RegisterRequest;
import com.laxman.codereviewassistant.entity.Role;
import com.laxman.codereviewassistant.entity.User;
import com.laxman.codereviewassistant.exception.EmailAlreadyExistsException;
import com.laxman.codereviewassistant.exception.InvalidCredentialsException;
import com.laxman.codereviewassistant.repository.UserRepository;
import com.laxman.codereviewassistant.security.JwtUtil;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public void register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // Self-registration only ever creates DEVELOPER accounts. ADMIN and
        // VIEWER are provisioned manually (direct DB update) for now — there's
        // no self-service path to elevate a role, which is intentional: letting
        // users grant themselves ADMIN via the register endpoint would defeat
        // the RBAC enforced in SecurityConfig. Revisit with an admin-only invite
        // endpoint if this becomes a real product.
        user.setRole(Role.DEVELOPER);

        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, 3600_000);
    }
}