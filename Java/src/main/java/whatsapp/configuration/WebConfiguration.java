package whatsapp.configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${profile.pic.upload-dir}")
    private String profilePicUploadDir;

    @PostConstruct
    public void initUploadDirectories() {
        createDirectoryIfNotExists(this.uploadDir, "User Files");
        createDirectoryIfNotExists(this.profilePicUploadDir, "Profile Pics");
    }

    private void createDirectoryIfNotExists(String dirPath, String label) {
        try {
            Path path = Paths.get(dirPath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created root {} directory at [{}]", label, path);
            }
        } catch (IOException e) {
            log.error("Failed to initialize {} directory: [{}]", label, dirPath, e);
        }
    }
}