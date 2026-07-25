package whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import whatsapp.entity.FileMetadata;
import whatsapp.repository.FileMetadataRepository;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDownloadService {

    private final FileMetadataRepository fileMetadataRepository;

    public record FileDownloadResult(Resource resource, FileMetadata metadata, String resolvedContentType) {}

    public FileDownloadResult getFileForDownload(String fileUuid, String currentUsername) throws IOException {
        FileMetadata metadata = fileMetadataRepository.findByFileUuid(fileUuid)
                .orElseThrow(() -> new FileNotFoundException("Requested file resource not found: " + fileUuid));

        boolean isSender = currentUsername.equalsIgnoreCase(metadata.getSenderUsername());
        boolean isReceiver = currentUsername.equalsIgnoreCase(metadata.getReceiverUsername());

        if (!isSender && !isReceiver) {
            log.warn("Unauthorized download attempt by user [{}] for file UUID [{}] owned by [{}] -> [{}]",
                    currentUsername, fileUuid, metadata.getSenderUsername(), metadata.getReceiverUsername());
            throw new AccessDeniedException("You do not have permission to access or download this file.");
        }

        Path path = Paths.get(metadata.getStoragePath()).toAbsolutePath().normalize();
        Resource resource = new UrlResource(path.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            log.error("File metadata exists in database but file is missing on storage disk: [{}]", metadata.getStoragePath());
            throw new FileNotFoundException("File content missing on server storage.");
        }

        String contentType = resolveContentType(path, metadata.getContentType());

        return new FileDownloadResult(resource, metadata, contentType);
    }

    private String resolveContentType(Path filePath, String dbContentType) {
        try {
            String probedType = Files.probeContentType(filePath);
            if (probedType != null && !probedType.isBlank()) {
                return probedType;
            }
        } catch (Exception ignored) {
        }

        if (dbContentType != null && !dbContentType.equalsIgnoreCase("application/octet-stream")) {
            return dbContentType;
        }

        return MediaTypeFactory.getMediaType(filePath.toString())
                .map(MediaType::toString)
                .orElse("application/octet-stream");
    }
}