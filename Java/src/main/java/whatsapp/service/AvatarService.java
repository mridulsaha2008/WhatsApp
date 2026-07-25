package whatsapp.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import whatsapp.entity.User;
import whatsapp.repository.UserRepository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    private final UserRepository userRepository;

    @Value("${profile.pic.upload-dir}")
    private String profilePicBasePath;

    @Value("${default.profile.pic.name:default.jpg}")
    private String defaultProfilePicName;

    @Value("${profile.pic.allowed-types}")
    private Set<String> allowedImageTypes;

    private final FileStorageService fileStorageService;
    private final UserService userService;

    public record AvatarResourceResult(Resource resource, String contentType) {
    }

    @PostConstruct
    public void initAllowedTypes() {
        if (this.allowedImageTypes != null) {
            this.allowedImageTypes = this.allowedImageTypes.stream()
                    .map(type -> type.toLowerCase().trim())
                    .collect(Collectors.toSet());
        }
    }

    public AvatarResourceResult getAvatarResourceByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username parameter cannot be empty.");
        }

        Path baseDir = Paths.get(profilePicBasePath).toAbsolutePath().normalize();

        Long userId = userService.getUserIdCached(username);

        File userAvatarFile = null;
        if (userId != null) {
            userAvatarFile = findUserAvatarFileById(baseDir.toFile(), userId);
        }

        Path filePath;
        if (userAvatarFile != null && userAvatarFile.exists()) {
            filePath = userAvatarFile.toPath();
        } else {
            filePath = baseDir.resolve(defaultProfilePicName).normalize();
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new FileNotFoundException("Avatar file unavailable on disk: " + filePath.getFileName());
            }

            String resolvedContentType = resolveContentType(filePath);

            if (!allowedImageTypes.contains(resolvedContentType)) {
                throw new UnsupportedMediaTypeStatusException("Unsupported media type: " + resolvedContentType);
            }

            return new AvatarResourceResult(resource, resolvedContentType);

        } catch (IOException e) {
            log.error("Error reading avatar file for username [{}]", username, e);
            throw new RuntimeException("Could not load avatar file.", e);
        }
    }

    @Transactional
    public void updateAvatar(MultipartFile file, String username) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot save an empty file.");
        }

        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        fileStorageService.saveProfilePic(file, user.getId());
    }

    private File findUserAvatarFileById(File directory, Long userId) {
        if (!directory.exists() || !directory.isDirectory()) {
            return null;
        }

        String prefix = userId + ".";
        File[] matchingFiles = directory.listFiles((_, name) ->
                name.startsWith(prefix) && !name.equalsIgnoreCase(defaultProfilePicName));

        if (matchingFiles != null && matchingFiles.length > 0) {
            return matchingFiles[0];
        }
        return null;
    }

    private String resolveContentType(Path filePath) {
        String contentType = null;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (Exception ignored) {
        }

        if (contentType == null || contentType.isBlank()) {
            contentType = MediaTypeFactory.getMediaType(filePath.toString())
                    .map(MediaType::toString)
                    .orElse("image/jpeg");
        }

        contentType = contentType.toLowerCase();
        if (contentType.contains(";")) {
            contentType = contentType.split(";")[0].trim();
        }

        return contentType;
    }
}