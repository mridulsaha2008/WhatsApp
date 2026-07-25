package whatsapp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import whatsapp.dto.LoginRequestDTO;
import whatsapp.dto.RegistrationRequestDTO;
import whatsapp.dto.AuthResponseDTO;
import whatsapp.service.AccessTokenService;
import whatsapp.service.LoginService;
import whatsapp.service.RefreshTokenService;
import whatsapp.service.RegistrationService;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RegistrationLoginController {

    private final LoginService loginService;
    private final RegistrationService registrationService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;

    @Value("${jwt.access.expiration}")
    private long jwtAccessExpiration;

    @Value("${jwt.access.name}")
    private String jwtAccessName;

    @Value("${jwt.refresh.name}")
    private String jwtRefreshName;

    @Value("${jwt.refresh.expiration}")
    private long jwtRefreshExpiration;

    @Value("${app.cookie.secure:false}")
    private boolean isCookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @PostMapping(path = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponseDTO> register(
            @Valid @RequestPart("user") RegistrationRequestDTO request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        log.debug("Processing registration request for username: [{}] and email: [{}]",
                request.getUsername(), request.getEmail());

        AuthResponseDTO authResponse = registrationService.register(request, file);

        return buildAuthResponseEntity(authResponse, request.getUsername());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginDTO) {
        log.debug("Processing login request for credential: [{}]", loginDTO.getCredential());

        AuthResponseDTO authResponse = loginService.login(loginDTO);

        return buildAuthResponseEntity(authResponse, authResponse.getUsername());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        log.debug("Processing logout request to invalidate session cookies");

        ResponseCookie cleanAccessCookie = createJwtCookie(jwtAccessName, "", Duration.ZERO);
        ResponseCookie cleanRefreshCookie = createJwtCookie(jwtRefreshName, "", Duration.ZERO);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanAccessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cleanRefreshCookie.toString())
                .build();
    }

    private ResponseEntity<AuthResponseDTO> buildAuthResponseEntity(AuthResponseDTO authResponse, String username) {
        ResponseCookie jwtAccessCookie = createJwtCookie(
                jwtAccessName,
                accessTokenService.getToken(username),
                Duration.ofMillis(jwtAccessExpiration)
        );

        ResponseCookie jwtRefreshCookie = createJwtCookie(
                jwtRefreshName,
                refreshTokenService.getToken(username),
                Duration.ofMillis(jwtRefreshExpiration)
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtAccessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
                .body(authResponse);
    }

    private ResponseCookie createJwtCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(isCookieSecure)
                .path("/")
                .maxAge(maxAge)
                .sameSite(cookieSameSite)
                .build();
    }
}