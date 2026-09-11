package com.flexcore.auth.service;

import com.flexcore.auth.dto.request.LoginRequest;
import com.flexcore.auth.dto.request.RefreshTokenRequest;
import com.flexcore.auth.dto.request.RegisterRequest;
import com.flexcore.auth.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(String refreshToken);
}
