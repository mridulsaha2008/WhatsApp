package whatsapp.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import whatsapp.entity.FileMetadata;
import whatsapp.repository.FileMetadataRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileMetadataRepository fileMetadataRepository;

    @Value("${file.upload-dir:uploads}")
    private String basePath;

    @Value("${profile.pic.upload-dir}")
    private String profilePicBasePath;

    @Value("${file.blocked-extensions}")
    private Set<String> blockedExtensions;

    @Value("${profile.pic.allowed-types}")
    private Set<String> allowedImageTypes;

    @Value("${profile.pic.allowed-size}")
    private long maxPicSize;

    @PostConstruct
    public void initImageAndExtensionSets() {
        if (this.allowedImageTypes != null) {
            this.allowedImageTypes = this.allowedImageTypes.stream()
                    .map(type -> type.toLowerCase().trim())
                    .collect(Collectors.toSet());
        }

        if (this.blockedExtensions != null) {
            this.blockedExtensions = this.blockedExtensions.stream()
                    .map(ext -> ext.toLowerCase().replace(".", "").trim())
                    .collect(Collectors.toSet());
        }
    }

    public String saveFileLocally(MultipartFile file, String sender, String receiver) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot save an empty file.");
        }

        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());

        String cleanSender = sanitizeDirectoryName(sender);
        String cleanReceiver = sanitizeDirectoryName(receiver);

        String rawFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "file");
        String originalFilename = StringUtils.cleanPath(rawFilename.replace("\0", ""));

        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Filename contains invalid path sequence: " + originalFilename);
        }

        String fileExtension = getFileExtension(originalFilename).toLowerCase();

        if (isExtensionBlocked(fileExtension)) {
            log.warn("Blocked upload attempt with restricted extension [{}] from user [{}]", fileExtension, sender);
            throw new IllegalArgumentException("File type not permitted for upload.");
        }

        Path baseDirectory = Paths.get(basePath).toAbsolutePath().normalize();
        Path targetDirectory = baseDirectory.resolve(Paths.get(year, month, day, cleanSender, cleanReceiver)).normalize();

        if (!targetDirectory.startsWith(baseDirectory)) {
            log.error("Directory traversal attempt detected! Target path [{}] outside base [{}]", targetDirectory, baseDirectory);
            throw new SecurityException("Cannot store file outside current target directory bounds.");
        }

        try {
            Files.createDirectories(targetDirectory);

            String fileUuid = UUID.randomUUID().toString();
            String storedFileName = fileUuid + fileExtension;
            Path targetFilePath = targetDirectory.resolve(storedFileName);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            String resolvedContentType = file.getContentType();
            if (resolvedContentType == null || resolvedContentType.equalsIgnoreCase("application/octet-stream")) {
                resolvedContentType = MediaTypeFactory
                        .getMediaType(originalFilename)
                        .map(org.springframework.http.MediaType::toString)
                        .orElse("application/octet-stream");
            }

            FileMetadata metadata = FileMetadata.builder()
                    .fileUuid(fileUuid)
                    .originalFileName(originalFilename)
                    .storagePath(targetFilePath.toString())
                    .contentType(resolvedContentType)
                    .fileSize(file.getSize())
                    .senderUsername(sender)
                    .receiverUsername(receiver)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            fileMetadataRepository.save(metadata);

            log.info("File stored securely with UUID [{}] for [{}] -> [{}]", fileUuid, sender, receiver);

            return "/api/files/download/" + fileUuid;

        } catch (IOException ex) {
            log.error("Failed to store file for sender [{}] -> receiver [{}]", sender, receiver, ex);
            throw new RuntimeException("Could not store file. Please try again later.", ex);
        }
    }

    public void saveProfilePic(MultipartFile file, Long id) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot save an empty file.");
        }
        if (id == null) {
            throw new IllegalArgumentException("User ID must not be null.");
        }

        validateImageFile(file);

        String rawFilename = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), ""));
        String fileExtension = getFileExtension(rawFilename).toLowerCase();

        if (!Set.of(".jpg", ".jpeg", ".png", ".webp").contains(fileExtension)) {
            fileExtension = ".jpeg";
        }

        String uniqueFileName = id + fileExtension;
        Path baseDirectory = Paths.get(profilePicBasePath).toAbsolutePath().normalize();

        try {
            Files.createDirectories(baseDirectory);

            deleteExistingUserAvatarsById(baseDirectory, id);

            Path targetFilePath = baseDirectory.resolve(uniqueFileName).normalize();

            if (!targetFilePath.startsWith(baseDirectory)) {
                throw new SecurityException("Invalid target file path.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Profile picture stored successfully for user ID [{}] at [{}]", id, targetFilePath);

        } catch (IOException ex) {
            log.error("Failed to store profile picture for user ID [{}]", id, ex);
            throw new RuntimeException("Could not store profile picture. Please try again later.", ex);
        }
    }

    private void deleteExistingUserAvatarsById(Path baseDirectory, Long id) {
        try {
            File dir = baseDirectory.toFile();
            if (dir.exists() && dir.isDirectory()) {
                String prefix = id + ".";
                File[] oldFiles = dir.listFiles((d, name) -> name.startsWith(prefix));
                if (oldFiles != null) {
                    for (File oldFile : oldFiles) {
                        if (oldFile.delete()) {
                            log.info("Deleted old avatar file for ID [{}]: [{}]", id, oldFile.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not delete previous avatar files for user ID [{}]: {}", id, e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return (lastDot == -1) ? "" : filename.substring(lastDot);
    }

    private boolean isExtensionBlocked(String fileExtension) {
        if (blockedExtensions == null || blockedExtensions.isEmpty() || fileExtension.isBlank()) {
            return false;
        }
        String cleanExt = fileExtension.toLowerCase().replace(".", "").trim();
        return blockedExtensions.contains(cleanExt);
    }

    private String sanitizeDirectoryName(String input) {
        if (input == null || input.isBlank()) {
            return "unknown";
        }
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public void validateImageFile(MultipartFile file) {
        if (mbConverter(file.getSize()) > maxPicSize) {
            throw new IllegalArgumentException("Profile picture size exceeds the maximum limit of " + maxPicSize + "MB.");
        }

        String rawContentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        String resolvedContentType = rawContentType;
        if (resolvedContentType == null || resolvedContentType.equalsIgnoreCase("application/octet-stream")) {
            resolvedContentType = MediaTypeFactory
                    .getMediaType(Objects.requireNonNullElse(originalFilename, "image.jpg"))
                    .map(org.springframework.http.MediaType::toString)
                    .orElse("");
        }

        resolvedContentType = resolvedContentType.toLowerCase();
        if (resolvedContentType.contains(";")) {
            resolvedContentType = resolvedContentType.split(";")[0].trim();
        }

        if (!allowedImageTypes.contains(resolvedContentType)) {
            throw new IllegalArgumentException("Invalid file type. Only image files are allowed.");
        }
    }

    private double mbConverter(long bytes) {
        return ((double) bytes / 1024) / 1024;
    }
}