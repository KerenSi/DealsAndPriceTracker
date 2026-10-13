package com.dealstracker.api;

import com.dealstracker.api.Dtos.AuthRequest;
import com.dealstracker.api.Dtos.AuthResponse;
import com.dealstracker.api.Dtos.UserDto;
import com.dealstracker.auth.AuthService;
import com.dealstracker.auth.AuthService.LoginResult;
import com.dealstracker.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody AuthRequest req) {
        return response(auth.register(req.email(), req.password()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest req) {
        return response(auth.login(req.email(), req.password()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        auth.logout(authorization);
    }

    @GetMapping("/me")
    public UserDto me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return user(auth.requireUser(authorization));
    }

    private static AuthResponse response(LoginResult r) {
        return new AuthResponse(r.token(), r.expiresAt(), user(r.user()));
    }

    static UserDto user(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.isShowSimilarProducts());
    }
}
