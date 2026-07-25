//package whatsapp.service;
//
//import jakarta.persistence.EntityManager;
//import lombok.AllArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.hibernate.search.engine.search.query.SearchResult;
//import org.hibernate.search.mapper.orm.Search;
//import org.hibernate.search.mapper.orm.session.SearchSession;
//import org.springframework.data.domain.Pageable;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import whatsapp.dto.AuthResponseDTO;
//import whatsapp.dto.UserDetailDTO;
//import whatsapp.entity.User;
//import whatsapp.repository.UserRepository;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Service
//@Slf4j
//@AllArgsConstructor
//public class UserService {
//    private final UserRepository userRepository;
//    private final EntityManager entityManager;
//
//    @Transactional(rollbackFor = Exception.class, readOnly = true)
//    public AuthResponseDTO getAuthDetail(String username) {
//        User user = userRepository.findByUsername(username)
//                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
//
//        return AuthResponseDTO.builder()
//                .username(user.getUsername())
//                .email(user.getEmail())
//                .fullName(user.getFullName())
//                .profilePhoto(user.getProfilePhoto())
//                .build();
//    }
//
//    @Transactional(rollbackFor = Exception.class, readOnly = true)
//    public UserDetailDTO getUserDetail(String username) {
//        User user = userRepository.findByUsername(username.trim())
//                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
//
//        return UserDetailDTO.builder()
//                .fullName(user.getFullName())
//                .username(user.getUsername())
//                .lastSeen(user.getLastSeen())
//                .profilePhoto(user.getProfilePhoto())
//                .build();
//    }
//
//    @Transactional(rollbackFor = Exception.class, readOnly = true)
//    public List<UserDetailDTO> searchUsers(String query, Pageable pageable) {
//        if (query == null || query.isBlank()) {
//            return List.of();
//        }
//
//        String cleanQuery = query.trim().toLowerCase();
//        SearchSession searchSession = Search.session(entityManager);
//
//        SearchResult<User> result = searchSession.search(User.class)
//                .where(f -> f.bool().with(b -> {
//                    b.should(f.match()
//                            .fields("fullName", "username")
//                            .matching(cleanQuery)
//                            .boost(10.0f));
//
//                    if (cleanQuery.length() >= 3) {
//                        b.should(f.match()
//                                .fields("fullName", "username")
//                                .matching(cleanQuery)
//                                .fuzzy(cleanQuery.length() > 5 ? 2 : 1)
//                                .boost(1.0f));
//                    }
//                }))
//                .sort(f -> f.composite()
//                        .add(f.score())
//                        .add(f.field("fullName_sort").asc())
//                        .add(f.field("username_sort").asc()))
//                .fetch((int) pageable.getOffset(), pageable.getPageSize());
//
//        return result.hits().stream()
//                .map(this::mapToUserDetailDTO)
//                .toList();
//    }
//
//    @Transactional(rollbackFor = Exception.class)
//    public void setUserLastSeen(String username) {
//        if (username == null || username.isBlank()) {
//            return;
//        }
//        try {
//            int updated = userRepository.updateLastSeenByUsername(username.trim(), LocalDateTime.now());
//            if (updated == 0) {
//                log.warn("Attempted to update lastSeen for non-existent user: [{}]", username);
//            }
//        } catch (Exception e) {
//            log.error("Failed to update lastSeen for user [{}]: {}", username, e.getMessage(), e);
//        }
//    }
//
//    private UserDetailDTO mapToUserDetailDTO(User user) {
//        return UserDetailDTO.builder()
//                .fullName(user.getFullName())
//                .username(user.getUsername())
//                .profilePhoto(user.getProfilePhoto())
//                .lastSeen(user.getLastSeen())
//                .build();
//    }
//}

package whatsapp.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import whatsapp.dto.AuthResponseDTO;
import whatsapp.dto.UserDetailDTO;
import whatsapp.entity.User;
import whatsapp.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final Map<String, Long> userIdCache = new ConcurrentHashMap<>();

    private final Map<String, Boolean> userExistenceCache = new ConcurrentHashMap<>();

    public boolean existsByUsernameCached(String username) {
        if (username == null || username.isBlank()) return false;
        String key = username.trim().toLowerCase();
        return userExistenceCache.computeIfAbsent(key, userRepository::existsByUsername);
    }

    public void cacheUserExistence(String username) {
        if (username != null && !username.isBlank()) {
            userExistenceCache.put(username.trim().toLowerCase(), true);
        }
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public AuthResponseDTO getAuthDetail(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        return AuthResponseDTO.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .profilePhoto(user.getProfilePhoto())
                .build();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public UserDetailDTO getUserDetail(String username) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        return UserDetailDTO.builder()
                .fullName(user.getFullName())
                .username(user.getUsername())
                .lastSeen(user.getLastSeen())
                .profilePhoto(user.getProfilePhoto())
                .build();
    }

    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public List<UserDetailDTO> searchUsers(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String cleanQuery = query.trim().toLowerCase();
        SearchSession searchSession = Search.session(entityManager);

        SearchResult<User> result = searchSession.search(User.class)
                .where(f -> f.bool().with(b -> {
                    b.should(f.match()
                            .fields("fullName", "username")
                            .matching(cleanQuery)
                            .boost(10.0f));

                    if (cleanQuery.length() >= 3) {
                        b.should(f.match()
                                .fields("fullName", "username")
                                .matching(cleanQuery)
                                .fuzzy(cleanQuery.length() > 5 ? 2 : 1)
                                .boost(1.0f));
                    }
                }))
                .sort(f -> f.composite()
                        .add(f.score())
                        .add(f.field("fullName_sort").asc())
                        .add(f.field("username_sort").asc()))
                .fetch((int) pageable.getOffset(), pageable.getPageSize());

        return result.hits().stream()
                .map(this::mapToUserDetailDTO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void setUserLastSeen(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        try {
            int updated = userRepository.updateLastSeenByUsername(username.trim(), LocalDateTime.now());
            if (updated == 0) {
                log.warn("Attempted to update lastSeen for non-existent user: [{}]", username);
            }
        } catch (Exception e) {
            log.error("Failed to update lastSeen for user [{}]: {}", username, e.getMessage(), e);
        }
    }

    public Long getUserIdCached(String username) {
        if (username == null || username.isBlank()) return null;
        String key = username.trim().toLowerCase();
        return userIdCache.computeIfAbsent(key, u ->
                userRepository.findByUsername(u).map(User::getId).orElse(null)
        );
    }

    private UserDetailDTO mapToUserDetailDTO(User user) {
        return UserDetailDTO.builder()
                .fullName(user.getFullName())
                .username(user.getUsername())
                .profilePhoto(user.getProfilePhoto())
                .lastSeen(user.getLastSeen())
                .build();
    }
}