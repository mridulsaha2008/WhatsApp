package whatsapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import whatsapp.service.AccessTokenService;
import whatsapp.service.RefreshTokenService;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final HandlerExceptionResolver resolver;

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

    @Autowired
    public JwtAuthenticationFilter(AccessTokenService accessTokenService,
                                   RefreshTokenService refreshTokenService,
                                   @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/login")
                || path.startsWith("/api/register");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String accessToken = extractCookie(request, JWT_ACCESS_NAME);

        try {
            if (accessToken != null && accessTokenService.isTokenValid(accessToken)) {
                String username = accessTokenService.extractUsername(accessToken);
                setAuthentication(username);
            } else {
                String refreshToken = extractCookie(request, JWT_REFRESH_NAME);

                if (refreshToken != null && refreshTokenService.isTokenValid(refreshToken)) {
                    String username = refreshTokenService.extractUsername(refreshToken);

                    if (username != null) {
                        String newAccessToken = accessTokenService.getToken(username);

                        ResponseCookie newAccessCookie = ResponseCookie.from(JWT_ACCESS_NAME, newAccessToken)
                                .httpOnly(true)
                                .secure(isCookieSecure)
                                .path("/")
                                .maxAge(Duration.ofMillis(jwtAccessExpiration))
                                .sameSite(cookieSameSite)
                                .build();

                        response.addHeader(HttpHeaders.SET_COOKIE, newAccessCookie.toString());

                        setAuthentication(username);
                    }
                }
            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            resolver.resolveException(request, response, null, e);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(String username) {
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private String extractCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}