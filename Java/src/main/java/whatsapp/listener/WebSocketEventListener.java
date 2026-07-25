package whatsapp.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import whatsapp.service.CallService;
import whatsapp.service.UserService;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {
    private final CallService callService;
    private final UserService userService;

    private final Map<String, Instant> lastSeenUpdateMap = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        Principal principal = event.getUser();
        if (principal != null && principal.getName() != null) {
            String username = principal.getName();

            Instant now = Instant.now();
            Instant lastUpdate = lastSeenUpdateMap.get(username);
            if (lastUpdate == null || Duration.between(lastUpdate, now).toSeconds() >= 60) {
                userService.setUserLastSeen(username);
                lastSeenUpdateMap.put(username, now);
            }

            log.info("WebSocket connected for user: [{}] (Session ID: {})", username, event.getMessage().getHeaders().get("simpSessionId"));
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal != null && principal.getName() != null) {
            String username = principal.getName();
            log.info("WebSocket disconnected for user: [{}]", username);
            try {
                callService.handleUserDisconnect(username);
            } catch (Exception ex) {
                log.error("Error handling user disconnect cleanup for user [{}]:", username, ex);
            }
        }
    }
}