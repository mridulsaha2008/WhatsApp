package whatsapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import whatsapp.dto.SignalDTO;
import whatsapp.service.CallService;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SignalingController {

    private final CallService callService;

    @MessageMapping("/call.signal")
    public void handleSignal(@Payload SignalDTO signal, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            log.warn("Unauthorized WebRTC signaling attempt detected.");
            throw new IllegalStateException("Unauthorized signaling request.");
        }

        if (signal == null) {
            throw new IllegalArgumentException("Signal payload cannot be null.");
        }

        String sender = principal.getName();
        log.debug("Received WebRTC signal type [{}] from sender [{}] for receiver [{}]",
                signal.getType(), sender, signal.getReceiver());

        callService.processAndRelaySignal(sender, signal);
    }
}