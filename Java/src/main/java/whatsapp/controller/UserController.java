package whatsapp.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import whatsapp.dto.AuthResponseDTO;
import whatsapp.dto.UserDetailDTO;
import whatsapp.service.AccessTokenService;
import whatsapp.service.RefreshTokenService;
import whatsapp.service.UserService;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    @Value("${jwt.access.expiration}")
    private long jwtAccessExpiration;

    @Value("${app.cookie.secure:false}")
    private boolean isCookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${jwt.access.name}")
    private String JWT_ACCESS_NAME;

    @Value("${jwt.refresh.name}")
    private String JWT_REFRESH_NAME;

    @GetMapping("/auth/validate")
    public ResponseEntity<?> validateToken(HttpServletRequest request) {
        String token = extractToken(request, JWT_ACCESS_NAME);

        if (token != null && accessTokenService.isTokenValid(token)) {
            String username = accessTokenService.extractUsername(token);
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "username", username
            ));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("authenticated", false, "message", "Invalid or expired access token"));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractToken(request, JWT_REFRESH_NAME);

        if (refreshToken == null || !refreshTokenService.isTokenValid(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired refresh token. Please log in again."));
        }

        String username = refreshTokenService.extractUsername(refreshToken);

        String newAccessToken = accessTokenService.getToken(username);

        ResponseCookie newAccessCookie = ResponseCookie.from(JWT_ACCESS_NAME, newAccessToken)
                .httpOnly(true)
                .secure(isCookieSecure)
                .path("/")
                .maxAge(Duration.ofMillis(jwtAccessExpiration))
                .sameSite(cookieSameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, newAccessCookie.toString());

        AuthResponseDTO authResponse = new AuthResponseDTO();
        authResponse.setUsername(username);

        return ResponseEntity.ok(authResponse);
    }

    @GetMapping("/auth/user/detail")
    public ResponseEntity<?> authDetail(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(userService.getAuthDetail(username));
    }

    @GetMapping("/user/detail/{user}")
    public ResponseEntity<?> userDetail(@PathVariable String user) {
        return ResponseEntity.ok(userService.getUserDetail(user));
    }

    @GetMapping("/user/search")
    public ResponseEntity<List<UserDetailDTO>> searchUsers(
            @RequestParam("query") String query,
            @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        List<UserDetailDTO> users = userService.searchUsers(query, pageable);
        return ResponseEntity.ok(users);
    }

    private String extractToken(HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            Optional<Cookie> cookie = Arrays.stream(request.getCookies())
                    .filter(c -> cookieName.equals(c.getName()))
                    .findFirst();
            if (cookie.isPresent() && !cookie.get().getValue().isBlank()) {
                return cookie.get().getValue();
            }
        }
        return null;
    }
}