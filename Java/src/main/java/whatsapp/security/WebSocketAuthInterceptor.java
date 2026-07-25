//package whatsapp.security;
//
//import lombok.AllArgsConstructor;
//import org.springframework.lang.NonNull;
//import org.springframework.messaging.Message;
//import org.springframework.messaging.MessageChannel;
//import org.springframework.messaging.simp.stomp.StompCommand;
//import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
//import org.springframework.messaging.support.ChannelInterceptor;
//import org.springframework.messaging.support.MessageHeaderAccessor;
//import org.springframework.security.access.AccessDeniedException;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.stereotype.Component;
//import whatsapp.service.AccessTokenService;
//
//import java.util.Collections;
//
//@Component
//@AllArgsConstructor
//public class WebSocketAuthInterceptor implements ChannelInterceptor {
//
//    private final AccessTokenService accessTokenService;
//
//    @Override
//    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
//        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
//
//        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
//
//            String accessToken = extractAccessToken(accessor);
//
//            if (accessToken != null && accessTokenService.isTokenValid(accessToken)) {
//                String username = accessTokenService.extractUsername(accessToken);
//                if (username != null) {
//                    UsernamePasswordAuthenticationToken auth =
//                            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
//                    accessor.setUser(auth);
//                    return message;
//                }
//            }
//
//            return null;
//        }
//
//        return message;
//    }
//
//    private String extractAccessToken(StompHeaderAccessor accessor) {
//        String authHeader = accessor.getFirstNativeHeader("Authorization");
//        if (authHeader == null) {
//            authHeader = accessor.getFirstNativeHeader("authorization");
//        }
//
//        if (authHeader != null && authHeader.startsWith("Bearer ")) {
//            return authHeader.substring(7);
//        } else if (authHeader != null && !authHeader.isBlank()) {
//            return authHeader;
//        }
//
//        String param = accessor.getFirstNativeHeader("token");
//        if (param != null && !param.isBlank()) {
//            return param;
//        }
//
//        return null;
//    }
//}

package whatsapp.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import whatsapp.service.AccessTokenService;
import whatsapp.service.RefreshTokenService;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.access.name}")
    private String JWT_ACCESS_NAME;
    @Value("${jwt.refresh.name}")
    private String JWT_REFRESH_NAME;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {

            String accessToken = extractToken(accessor, JWT_ACCESS_NAME);

            if (accessToken != null && accessTokenService.isTokenValid(accessToken)) {
                String username = accessTokenService.extractUsername(accessToken);
                if (username != null && !username.isBlank()) {
                    setStompAuthentication(accessor, username);
                    log.info("WebSocket authenticated via Access Token for user: [{}]", username);
                    return message;
                }
            }

            log.info("Access token invalid or expired. Attempting WebSocket auth via Refresh Token...");
            String refreshToken = extractToken(accessor, JWT_REFRESH_NAME);

            if (refreshToken != null && refreshTokenService.isTokenValid(refreshToken)) {
                String username = refreshTokenService.extractUsername(refreshToken);

                if (username != null && !username.isBlank()) {
                    String newAccessToken = accessTokenService.getToken(username);

                    if (accessor.getSessionAttributes() != null) {
                        accessor.getSessionAttributes().put(JWT_ACCESS_NAME, newAccessToken);
                    }

                    setStompAuthentication(accessor, username);
                    log.info("WebSocket successfully authenticated via Refresh Token fallback for user: [{}]", username);
                    return message;
                }
            }

            log.warn("WebSocket authentication failed: Neither valid Access Token nor Refresh Token was present.");
            throw new AccessDeniedException("Unauthorized WebSocket connection attempt.");
        }

        return message;
    }

    private void setStompAuthentication(StompHeaderAccessor accessor, String username) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
        accessor.setUser(auth);
    }

    private String extractToken(StompHeaderAccessor accessor, String tokenKey) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object tokenObj = sessionAttributes.get(tokenKey);
            if (tokenObj instanceof String token && !token.isBlank()) {
                return token;
            }
        }
        return null;
    }
}