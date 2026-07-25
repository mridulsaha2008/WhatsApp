package whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import whatsapp.dto.MessageRequestDTO;
import whatsapp.dto.MessageResponseDTO;
import whatsapp.entity.Message;
import whatsapp.entity.User;
import whatsapp.repository.MessageRepository;
import whatsapp.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    // IMPORTANT: Inject UserService instead of UserRepository for cached lookups
    private final UserService userService;

    @Transactional(rollbackFor = Exception.class)
    public MessageResponseDTO sendMessage(String senderUsername, MessageRequestDTO request) {
        if (request == null || request.getReceiver() == null || request.getContent() == null) {
            throw new IllegalArgumentException("Receiver and content fields are required.");
        }

        String sender = senderUsername.trim();
        String receiver = request.getReceiver().trim();

        if (sender.equalsIgnoreCase(receiver)) {
            throw new IllegalArgumentException("You cannot send messages to yourself.");
        }

        // OPTIMIZATION 1: Use the Memory Cache instead of hitting Postgres
        if (!userService.existsByUsernameCached(receiver)) {
            throw new IllegalArgumentException("Target recipient user does not exist.");
        }

        String messageType = (request.getType() != null && !request.getType().isBlank())
                ? request.getType().trim().toUpperCase()
                : "TEXT";

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .type(messageType)
                .text(request.getContent().trim())
                .dateTime(LocalDateTime.now())
                .isRead(false)
                .build();

        Message saved = messageRepository.save(message);
        MessageResponseDTO response = mapToDTO(saved);

        log.info("Pushing WebSocket chat message from [{}] to receiver [{}]", sender, receiver);

        messagingTemplate.convertAndSendToUser(
                receiver,
                "/queue/messages",
                response
        );

        messagingTemplate.convertAndSendToUser(
                sender,
                "/queue/messages",
                response
        );

        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public MessageResponseDTO sendMessage(String senderUsername, String receiverUsername, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty.");
        }

        String fileUrl = fileStorageService.saveFileLocally(multipartFile, senderUsername, receiverUsername);

        MessageRequestDTO messageRequest = getMessageRequestDTO(receiverUsername, multipartFile, fileUrl);

        return sendMessage(senderUsername, messageRequest);
    }

    private static MessageRequestDTO getMessageRequestDTO(String receiverUsername, MultipartFile multipartFile, String downloadUrl) {
        String contentType = multipartFile.getContentType();
        String messageType = "FILE";

        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                messageType = "IMAGE";
            } else if (contentType.startsWith("video/")) {
                messageType = "VIDEO";
            } else if (contentType.startsWith("audio/")) {
                messageType = "AUDIO";
            }
        }

        MessageRequestDTO messageRequest = new MessageRequestDTO();
        messageRequest.setReceiver(receiverUsername);
        messageRequest.setType(messageType);
        messageRequest.setContent(downloadUrl);
        return messageRequest;
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getHomepageConversations(String currentUser) {
        return messageRepository.findHomepageConversations(currentUser)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getConversations(String user1, String user2) {
        return messageRepository.findConversations(user1, user2)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getTop50Before(String user1, String user2, LocalDateTime dateTime) {
        return messageRepository.findTop50Before(user1, user2, dateTime)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getTop50BeforeInclusive(String user1, String user2, LocalDateTime dateTime) {
        return messageRepository.findTop50BeforeInclusive(user1, user2, dateTime)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getTop50After(String user1, String user2, LocalDateTime dateTime) {
        return messageRepository.findTop50After(user1, user2, dateTime)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getTop50AfterInclusive(String user1, String user2, LocalDateTime dateTime) {
        return messageRepository.findTop50AfterInclusive(user1, user2, dateTime)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getBetween(String user1, String user2, LocalDateTime start, LocalDateTime end) {
        return messageRepository.findBetween(user1, user2, start, end)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getBetweenInclusive(String user1, String user2, LocalDateTime start, LocalDateTime end) {
        return messageRepository.findBetweenInclusive(user1, user2, start, end)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getBetweenInclusiveLeft(String user1, String user2, LocalDateTime start, LocalDateTime end) {
        return messageRepository.findBetweenInclusiveLeft(user1, user2, start, end)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<MessageResponseDTO> getBetweenInclusiveRight(String user1, String user2, LocalDateTime start, LocalDateTime end) {
        return messageRepository.findBetweenInclusiveRight(user1, user2, start, end)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public int markAsRead(String sender, String receiver) {
        int updatedCount = messageRepository.markAsRead(sender, receiver);

        if (updatedCount > 0) {
            messagingTemplate.convertAndSendToUser(
                    sender,
                    "/queue/read-receipts",
                    Map.of(
                            "readBy", receiver,
                            "count", updatedCount,
                            "timestamp", LocalDateTime.now().toString()
                    )
            );
        }

        return updatedCount;
    }

    private MessageResponseDTO mapToDTO(Message message) {
        return MessageResponseDTO.builder()
                .id(message.getId())
                .sender(message.getSender())
                .receiver(message.getReceiver())
                .type(message.getType())
                .text(message.getText())
                .dateTime(message.getDateTime())
                .isRead(message.getIsRead())
                .build();
    }
}