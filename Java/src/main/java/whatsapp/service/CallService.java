package whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import whatsapp.dto.CallResponseDTO;
import whatsapp.dto.MessageRequestDTO;
import whatsapp.dto.SignalDTO;
import whatsapp.entity.Call;
import whatsapp.repository.CallRepository;
import whatsapp.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final SimpMessagingTemplate messagingTemplate;
    private final CallRepository callRepository;
    private final ChatService chatService;
    private final UserService userService;

    private final Map<String, Long> activeCallRecordIds = new ConcurrentHashMap<>();
    private final Map<String, String> activeCalls = new ConcurrentHashMap<>();

    private static final Set<String> ALLOWED_SIGNAL_TYPES = Set.of(
            "VIDEO_CALL_INITIATE",
            "AUDIO_CALL_INITIATE",
            "OFFER",
            "ANSWER",
            "ICE_CANDIDATE",
            "DECLINE",
            "END_CALL",
            "BUSY"
    );

    @Transactional(rollbackFor = Exception.class)
    public void processAndRelaySignal(String senderUsername, SignalDTO signal) {
        if (signal == null || signal.getReceiver() == null || signal.getType() == null) {
            throw new IllegalArgumentException("Signal Payload, Receiver, and Type are required.");
        }

        String senderKey = senderUsername.trim().toLowerCase();
        String receiverUsername = signal.getReceiver().trim();
        String receiverKey = receiverUsername.toLowerCase();
        String signalType = signal.getType().trim().toUpperCase();

        if (senderKey.equalsIgnoreCase(receiverKey)) {
            throw new IllegalArgumentException("You cannot send call signals to yourself.");
        }

        if (!ALLOWED_SIGNAL_TYPES.contains(signalType)) {
            throw new IllegalArgumentException("Invalid signal type: " + signalType);
        }

        log.debug("Processing WebRTC signal [{}] from [{}] to [{}]", signalType, senderUsername, receiverUsername);

        switch (signalType) {
            case "VIDEO_CALL_INITIATE", "AUDIO_CALL_INITIATE" -> {
                if (!userService.existsByUsernameCached(receiverUsername)) {
                    throw new IllegalArgumentException("Target user does not exist.");
                }
                String callType = signalType.startsWith("VIDEO") ? "VIDEO" : "AUDIO";

                if (isUserInCall(receiverKey) || isUserInCall(senderKey)) {
                    logCallAttempt(senderUsername, receiverUsername, callType, "BUSY");
                    sendDirectSignal(senderUsername, receiverUsername, "BUSY", null);
                    postCallMessageToChat(senderUsername, receiverUsername, "Busy (" + callType.toLowerCase() + " call)");
                    return;
                }

                activeCalls.put(senderKey, receiverKey);
                activeCalls.put(receiverKey, senderKey);

                Call callRecord = Call.builder()
                        .caller(senderUsername)
                        .receiver(receiverUsername)
                        .callType(callType)
                        .status("MISSED")
                        .startTime(LocalDateTime.now())
                        .build();

                Call savedCall = callRepository.save(callRecord);

                activeCallRecordIds.put(senderKey, savedCall.getId());
                activeCallRecordIds.put(receiverKey, savedCall.getId());

                postCallMessageToChat(senderUsername, receiverUsername, callType);
            }

            case "ANSWER" -> {
                Long callId = activeCallRecordIds.get(senderKey);
                if (callId != null) {
                    callRepository.updateCallStatus(callId, "CONNECTED");
                }
            }

            case "DECLINE" -> {
                Long callId = activeCallRecordIds.get(senderKey);
                if (callId != null) {
                    finalizeCallRecord(callId, "DECLINED");
                }
                clearCallSession(senderKey, receiverKey);
            }

            case "END_CALL" -> {
                Long callId = activeCallRecordIds.get(senderKey);
                if (callId != null) {
                    callRepository.findById(callId).ifPresent(call -> {
                        String finalStatus = "CONNECTED".equalsIgnoreCase(call.getStatus()) ? "CONNECTED" : "MISSED";
                        finalizeCallRecord(callId, finalStatus);
                    });
                }
                clearCallSession(senderKey, receiverKey);
            }

            case "ICE_CANDIDATE" -> {
                String currentPeer = activeCalls.get(senderKey);
                if (currentPeer != null && !currentPeer.equals(receiverKey)) {
                    throw new IllegalStateException("Unauthorized signaling context.");
                }
            }
        }

        sendDirectSignal(receiverUsername, senderUsername, signalType, signal.getData());
    }

    private void postCallMessageToChat(String sender, String receiver, String textContent) {
        MessageRequestDTO chatMessage = new MessageRequestDTO();
        chatMessage.setReceiver(receiver);
        chatMessage.setType("CALL");
        chatMessage.setContent(textContent);

        chatService.sendMessage(sender, chatMessage);
    }

    private void finalizeCallRecord(Long callId, String status) {
        LocalDateTime now = LocalDateTime.now();
        callRepository.findById(callId).ifPresent(call -> {
            long seconds = 0L;
            if (call.getStartTime() != null) {
                seconds = Duration.between(call.getStartTime(), now).getSeconds();
            }
            callRepository.finalizeCall(callId, status, now, seconds);
        });
    }

    private void logCallAttempt(String caller, String receiver, String callType, String status) {
        LocalDateTime now = LocalDateTime.now();
        Call call = Call.builder()
                .caller(caller)
                .receiver(receiver)
                .callType(callType)
                .status(status)
                .startTime(now)
                .endTime(now)
                .durationInSeconds(0L)
                .build();
        callRepository.save(call);
    }

    private void sendDirectSignal(String targetQueueUser, String signalOriginator, String type, Object data) {
        SignalDTO outboundSignal = new SignalDTO(signalOriginator, type, data);
        messagingTemplate.convertAndSendToUser(
                targetQueueUser,
                "/queue/signals",
                outboundSignal
        );
    }

    public boolean isUserInCall(String username) {
        if (username == null) return false;
        return activeCalls.containsKey(username.toLowerCase());
    }

    public void clearCallSession(String user1, String user2) {
        if (user1 != null) {
            String u1Key = user1.toLowerCase();
            activeCalls.remove(u1Key);
            activeCallRecordIds.remove(u1Key);
        }
        if (user2 != null) {
            String u2Key = user2.toLowerCase();
            activeCalls.remove(u2Key);
            activeCallRecordIds.remove(u2Key);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleUserDisconnect(String username) {
        if (username == null || username.isBlank()) return;
        String userKey = username.trim().toLowerCase();

        Long callId = activeCallRecordIds.get(userKey);
        String peerUserKey = activeCalls.get(userKey);

        if (callId != null) {
            callRepository.findById(callId).ifPresent(call -> {
                String finalStatus = "CONNECTED".equalsIgnoreCase(call.getStatus()) ? "CONNECTED" : "MISSED";
                finalizeCallRecord(callId, finalStatus);
            });
        }

        if (peerUserKey != null) {
            sendDirectSignal(peerUserKey, username, "END_CALL", null);
            clearCallSession(userKey, peerUserKey);
        } else {
            activeCalls.remove(userKey);
            activeCallRecordIds.remove(userKey);
        }
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getAllCalls(String username) {
        return callRepository.findAllCalls(username).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getTop50Before(String username, LocalDateTime before) {
        return callRepository.findAllCallsBefore(username, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getTop50BeforeInclusive(String username, LocalDateTime before) {
        return callRepository.findAllCallsBeforeInclusive(username, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getTop50After(String username, LocalDateTime after) {
        return callRepository.findAllCallsAfter(username, after).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getTop50AfterInclusive(String username, LocalDateTime after) {
        return callRepository.findAllCallsAfterInclusive(username, after).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getBetween(String username, LocalDateTime after, LocalDateTime before) {
        return callRepository.findAllCallsBetween(username, after, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getBetweenInclusive(String username, LocalDateTime after, LocalDateTime before) {
        return callRepository.findAllCallsBetweenInclusive(username, after, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getBetweenAfterInclusive(String username, LocalDateTime after, LocalDateTime before) {
        return callRepository.findAllCallsBetweenAfterInclusive(username, after, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getBetweenBeforeInclusive(String username, LocalDateTime after, LocalDateTime before) {
        return callRepository.findAllCallsBetweenBeforeInclusive(username, after, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getCallsBetweenUsers(String user1, String user2) {
        return callRepository.findCallBetweenUsers(user1, user2).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getCallsBetweenUsersTop50Before(String user1, String user2, LocalDateTime before) {
        return callRepository.findCallBetweenUsersBefore(user1, user2, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getCallsBetweenUsersTop50BeforeInclusive(String user1, String user2, LocalDateTime before) {
        return callRepository.findCallBetweenUsersBeforeInclusive(user1, user2, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getCallsBetweenUsersTop50After(String user1, String user2, LocalDateTime after) {
        return callRepository.findCallBetweenUsersAfter(user1, user2, after).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getCallsBetweenUsersTop50AfterInclusive(String user1, String user2, LocalDateTime after) {
        return callRepository.findCallBetweenUsersAfterInclusive(user1, user2, after).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getCallsBetweenUsersBetween(String user1, String user2, LocalDateTime after, LocalDateTime before) {
        return callRepository.findCallBetweenUsersBetween(user1, user2, after, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getCallsBetweenUsersBetweenInclusive(String user1, String user2, LocalDateTime after, LocalDateTime before) {
        return callRepository.findCallBetweenUsersBetweenInclusive(user1, user2, after, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getCallsBetweenUsersBetweenAfterInclusive(String user1, String user2, LocalDateTime after, LocalDateTime before) {
        return callRepository.findCallBetweenUsersBetweenAfterInclusive(user1, user2, after, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<CallResponseDTO> getCallsBetweenUsersBetweenBeforeInclusive(String user1, String user2, LocalDateTime after, LocalDateTime before) {
        return callRepository.findCallBetweenUsersBetweenBeforeInclusive(user1, user2, after, before).stream()
                .map(this::mapToDTO)
                .toList();
    }

    private CallResponseDTO mapToDTO(Call call) {
        return CallResponseDTO.builder()
                .id(call.getId())
                .caller(call.getCaller())
                .receiver(call.getReceiver())
                .callType(call.getCallType())
                .status(call.getStatus())
                .startTime(call.getStartTime())
                .endTime(call.getEndTime())
                .durationInSeconds(call.getDurationInSeconds())
                .build();
    }
}