package whatsapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import whatsapp.service.FileDownloadService;
import whatsapp.service.FileDownloadService.FileDownloadResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileDownloadController {

    private final FileDownloadService fileDownloadService;

    @GetMapping("/download/{fileUuid}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileUuid, Principal principal) throws IOException {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        FileDownloadResult result = fileDownloadService.getFileForDownload(fileUuid, principal.getName());

        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(result.metadata().getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.resolvedContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentLength(result.metadata().getFileSize())
                .body(result.resource());
    }
}