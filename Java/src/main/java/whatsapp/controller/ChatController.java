package whatsapp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import whatsapp.dto.MessageRequestDTO;
import whatsapp.dto.MessageResponseDTO;
import whatsapp.service.ChatService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public MessageResponseDTO sendMessage(@Valid @Payload MessageRequestDTO request, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalStateException("Unauthenticated user cannot send messages.");
        }
        log.debug("WebSocket chat message request received from user [{}]", principal.getName());
        return chatService.sendMessage(principal.getName(), request);
    }

    @PostMapping(value = "/api/chat/send-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponseDTO> sendFileMessage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("receiver") String receiver,
            Principal principal) {

        log.debug("HTTP file message upload request from [{}] to [{}]", principal.getName(), receiver);
        MessageResponseDTO response = chatService.sendMessage(principal.getName(), receiver, file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/chat/homepage")
    public ResponseEntity<List<MessageResponseDTO>> getHomepageConversations(Principal principal) {
        log.debug("Fetching homepage conversations for user [{}]", principal.getName());
        List<MessageResponseDTO> conversations = chatService.getHomepageConversations(principal.getName());
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/api/chat/conversation/{otherUser}")
    public ResponseEntity<List<MessageResponseDTO>> getConversationHistory(
            @PathVariable String otherUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeInclusive,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime afterInclusive,
            Principal principal) {

        if (otherUser == null || otherUser.isBlank()) {
            throw new IllegalArgumentException("Target user parameter cannot be blank.");
        }

        String currentUser = principal.getName();
        String targetUser = otherUser.trim();
        log.debug("Fetching chat history between [{}] and [{}]", currentUser, targetUser);

        List<MessageResponseDTO> history = resolveConversationHistory(
                currentUser, targetUser, before, beforeInclusive, after, afterInclusive
        );

        return ResponseEntity.ok(history);
    }

    @PutMapping("/api/chat/read/{sender}")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable String sender, Principal principal) {
        if (sender == null || sender.isBlank()) {
            throw new IllegalArgumentException("Sender parameter cannot be blank.");
        }

        String currentUser = principal.getName();
        String messageSender = sender.trim();

        int count = chatService.markAsRead(messageSender, currentUser);
        log.debug("User [{}] marked {} messages from [{}] as read", currentUser, count, messageSender);

        return ResponseEntity.ok(Map.of(
                "markedCount", count,
                "message", count + " messages marked as read."
        ));
    }

    private List<MessageResponseDTO> resolveConversationHistory(
            String currentUser, String otherUser,
            LocalDateTime before, LocalDateTime beforeInclusive,
            LocalDateTime after, LocalDateTime afterInclusive) {

        if (after != null && before != null) {
            return chatService.getBetween(currentUser, otherUser, after, before);
        } else if (afterInclusive != null && beforeInclusive != null) {
            return chatService.getBetweenInclusive(currentUser, otherUser, afterInclusive, beforeInclusive);
        } else if (afterInclusive != null && before != null) {
            return chatService.getBetweenInclusiveLeft(currentUser, otherUser, afterInclusive, before);
        } else if (after != null && beforeInclusive != null) {
            return chatService.getBetweenInclusiveRight(currentUser, otherUser, after, beforeInclusive);
        } else if (before != null) {
            return chatService.getTop50Before(currentUser, otherUser, before);
        } else if (beforeInclusive != null) {
            return chatService.getTop50BeforeInclusive(currentUser, otherUser, beforeInclusive);
        } else if (after != null) {
            return chatService.getTop50After(currentUser, otherUser, after);
        } else if (afterInclusive != null) {
            return chatService.getTop50AfterInclusive(currentUser, otherUser, afterInclusive);
        }

        return chatService.getConversations(currentUser, otherUser);
    }
}