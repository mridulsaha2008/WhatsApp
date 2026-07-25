package whatsapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import whatsapp.dto.CallResponseDTO;
import whatsapp.service.CallService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @GetMapping("/history")
    public ResponseEntity<List<CallResponseDTO>> getUserCallHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeInclusive,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime afterInclusive,
            Principal principal) {

        String currentUser = principal.getName();
        log.debug("Fetching call history for user [{}]", currentUser);

        List<CallResponseDTO> history = resolveUserCallHistory(currentUser, before, beforeInclusive, after, afterInclusive);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history/{otherUser}")
    public ResponseEntity<List<CallResponseDTO>> getCallHistoryWithUser(
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
        log.debug("Fetching call history between [{}] and [{}]", currentUser, targetUser);

        List<CallResponseDTO> history = resolvePeerCallHistory(currentUser, targetUser, before, beforeInclusive, after, afterInclusive);
        return ResponseEntity.ok(history);
    }

    private List<CallResponseDTO> resolveUserCallHistory(
            String currentUser,
            LocalDateTime before, LocalDateTime beforeInclusive,
            LocalDateTime after, LocalDateTime afterInclusive) {

        if (after != null && before != null) {
            return callService.getBetween(currentUser, after, before);
        } else if (afterInclusive != null && beforeInclusive != null) {
            return callService.getBetweenInclusive(currentUser, afterInclusive, beforeInclusive);
        } else if (afterInclusive != null && before != null) {
            return callService.getBetweenAfterInclusive(currentUser, afterInclusive, before);
        } else if (after != null && beforeInclusive != null) {
            return callService.getBetweenBeforeInclusive(currentUser, after, beforeInclusive);
        } else if (before != null) {
            return callService.getTop50Before(currentUser, before);
        } else if (beforeInclusive != null) {
            return callService.getTop50BeforeInclusive(currentUser, beforeInclusive);
        } else if (after != null) {
            return callService.getTop50After(currentUser, after);
        } else if (afterInclusive != null) {
            return callService.getTop50AfterInclusive(currentUser, afterInclusive);
        }

        return callService.getAllCalls(currentUser);
    }

    private List<CallResponseDTO> resolvePeerCallHistory(
            String currentUser, String otherUser,
            LocalDateTime before, LocalDateTime beforeInclusive,
            LocalDateTime after, LocalDateTime afterInclusive) {

        if (after != null && before != null) {
            return callService.getCallsBetweenUsersBetween(currentUser, otherUser, after, before);
        } else if (afterInclusive != null && beforeInclusive != null) {
            return callService.getCallsBetweenUsersBetweenInclusive(currentUser, otherUser, afterInclusive, beforeInclusive);
        } else if (afterInclusive != null && before != null) {
            return callService.getCallsBetweenUsersBetweenAfterInclusive(currentUser, otherUser, afterInclusive, before);
        } else if (after != null && beforeInclusive != null) {
            return callService.getCallsBetweenUsersBetweenBeforeInclusive(currentUser, otherUser, after, beforeInclusive);
        } else if (before != null) {
            return callService.getCallsBetweenUsersTop50Before(currentUser, otherUser, before);
        } else if (beforeInclusive != null) {
            return callService.getCallsBetweenUsersTop50BeforeInclusive(currentUser, otherUser, beforeInclusive);
        } else if (after != null) {
            return callService.getCallsBetweenUsersTop50After(currentUser, otherUser, after);
        } else if (afterInclusive != null) {
            return callService.getCallsBetweenUsersTop50AfterInclusive(currentUser, otherUser, afterInclusive);
        }

        return callService.getCallsBetweenUsers(currentUser, otherUser);
    }
}