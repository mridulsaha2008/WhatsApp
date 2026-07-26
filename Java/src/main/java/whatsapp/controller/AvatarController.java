package whatsapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import whatsapp.service.AvatarService;
import whatsapp.service.AvatarService.AvatarResourceResult;

import java.security.Principal;
import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;
    @Value("${profile.pic.cache.max-age}")
    private Duration profilePicMaxAge;

    @GetMapping("/avatar/{username}")
    public ResponseEntity<Resource> getProfileAvatar(@PathVariable String username, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AvatarResourceResult avatarResult = avatarService.getAvatarResourceByUsername(username);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatarResult.contentType()))
                .cacheControl(CacheControl.maxAge(profilePicMaxAge).cachePrivate().mustRevalidate())
                .body(avatarResult.resource());
    }

    @PutMapping(path = "/avatar/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> avatarUpdate(
            @RequestPart(value = "file") MultipartFile file,
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        avatarService.updateAvatar(file, principal.getName());
        return ResponseEntity.ok().build();
    }
}
