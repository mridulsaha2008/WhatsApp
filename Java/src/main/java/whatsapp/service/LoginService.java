package whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import whatsapp.dto.AuthResponseDTO;
import whatsapp.dto.LoginRequestDTO;
import whatsapp.entity.User;
import whatsapp.repository.UserRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public AuthResponseDTO login(LoginRequestDTO loginDTO) {
        if (loginDTO == null
                || loginDTO.getCredential() == null
                || loginDTO.getCredential().isBlank()
                || loginDTO.getPassword() == null
                || loginDTO.getPassword().isBlank()) {
            throw new IllegalArgumentException("All login fields are required.");
        }

        String credential = loginDTO.getCredential().trim();

        User user = userRepository.findByEmailIgnoreCaseOrUsername(credential, credential)
                .orElseThrow(() -> {
                    log.warn("Failed login attempt for credential [{}] - User not found.", credential);
                    return new IllegalStateException("Invalid Username/Email or Password.");
                });

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            log.warn("Failed login attempt for user [{}] - Invalid password.", user.getUsername());
            throw new IllegalStateException("Invalid Username/Email or Password.");
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            log.warn("Login blocked for user [{}] - Account is deactivated.", user.getUsername());
            throw new IllegalStateException("User account is deactivated. Please contact support.");
        }

        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);

        log.info("User [{}] logged in successfully.", user.getUsername());

        return AuthResponseDTO.builder()
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .profilePhoto(user.getProfilePhoto())
                .build();
    }
}