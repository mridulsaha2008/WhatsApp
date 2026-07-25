//package whatsapp.service;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.text.WordUtils;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.multipart.MultipartFile;
//import whatsapp.dto.RegistrationRequestDTO;
//import whatsapp.dto.AuthResponseDTO;
//import whatsapp.entity.User;
//import whatsapp.repository.UserRepository;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class RegistrationService {
//
//    private final UserRepository userRepository;
//    private final PasswordEncoder passwordEncoder;
//    private final FileStorageService fileStorageService;
//
//    @Transactional(rollbackFor = Exception.class)
//    public AuthResponseDTO register(RegistrationRequestDTO requestUser, MultipartFile file) {
//        if (requestUser == null
//                || isBlank(requestUser.getFirstName())
//                || isBlank(requestUser.getLastName())
//                || isBlank(requestUser.getEmail())
//                || isBlank(requestUser.getUsername())
//                || isBlank(requestUser.getPassword())) {
//            throw new IllegalArgumentException("All registration fields are required.");
//        }
//
//        String normalizedEmail = requestUser.getEmail().trim().toLowerCase();
//        String normalizedUsername = requestUser.getUsername().trim();
//
//        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
//            throw new IllegalArgumentException("Email is already registered.");
//        }
//
//        if (userRepository.existsByUsername(normalizedUsername)) {
//            throw new IllegalArgumentException("Username is already taken.");
//        }
//
//        if (file != null && !file.isEmpty()) {
//            fileStorageService.validateImageFile(file);
//        }
//
//        String avatarUrl = "/api/users/avatar/" + normalizedUsername;
//
//        String firstName = WordUtils.capitalizeFully(requestUser.getFirstName().trim());
//        String lastName = WordUtils.capitalizeFully(requestUser.getLastName().trim());
//        String fullName = (firstName + " " + lastName).trim();
//
//        User unsavedUser = User.builder()
//                .fullName(fullName)
//                .email(normalizedEmail)
//                .username(normalizedUsername)
//                .password(passwordEncoder.encode(requestUser.getPassword()))
//                .profilePhoto(avatarUrl)
//                .build();
//
//        User savedUser = userRepository.save(unsavedUser);
//
//        if (file != null && !file.isEmpty()) {
//            fileStorageService.saveProfilePic(file, savedUser.getId());
//        }
//
//        log.info("New user registered successfully: [{}] with avatar URL [{}]", savedUser.getUsername(), avatarUrl);
//
//        return AuthResponseDTO.builder()
//                .fullName(savedUser.getFullName())
//                .username(savedUser.getUsername())
//                .email(savedUser.getEmail())
//                .profilePhoto(savedUser.getProfilePhoto())
//                .build();
//    }
//
//    private boolean isBlank(String str) {
//        return str == null || str.isBlank();
//    }
//}

package whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.WordUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import whatsapp.dto.RegistrationRequestDTO;
import whatsapp.dto.AuthResponseDTO;
import whatsapp.entity.User;
import whatsapp.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final UserService userService;

    @Transactional(rollbackFor = Exception.class)
    public AuthResponseDTO register(RegistrationRequestDTO requestUser, MultipartFile file) {
        if (requestUser == null || isBlank(requestUser.getFirstName()) || isBlank(requestUser.getLastName()) || isBlank(requestUser.getEmail()) || isBlank(requestUser.getUsername()) || isBlank(requestUser.getPassword())) {
            throw new IllegalArgumentException("All registration fields are required.");
        }

        String normalizedEmail = requestUser.getEmail().trim().toLowerCase();
        String normalizedUsername = requestUser.getUsername().trim();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered.");
        }

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username is already taken.");
        }

        if (file != null && !file.isEmpty()) {
            fileStorageService.validateImageFile(file);
        }

        String avatarUrl = "/api/users/avatar/" + normalizedUsername;
        String fullName = (WordUtils.capitalizeFully(requestUser.getFirstName().trim()) + " " + WordUtils.capitalizeFully(requestUser.getLastName().trim())).trim();

        User unsavedUser = User.builder()
                .fullName(fullName)
                .email(normalizedEmail)
                .username(normalizedUsername)
                .password(passwordEncoder.encode(requestUser.getPassword()))
                .profilePhoto(avatarUrl)
                .build();

        User savedUser = userRepository.save(unsavedUser);

        userService.cacheUserExistence(savedUser.getUsername());

        if (file != null && !file.isEmpty()) {
            fileStorageService.saveProfilePic(file, savedUser.getId());
        }

        log.info("New user registered successfully: [{}] with avatar URL [{}]", savedUser.getUsername(), avatarUrl);

        return AuthResponseDTO.builder()
                .fullName(savedUser.getFullName())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .profilePhoto(savedUser.getProfilePhoto())
                .build();
    }

    private boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
}