package whatsapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService extends TokenService {
    public AccessTokenService(
            @Value("${jwt.access.secret}") String secretKey,
            @Value("${jwt.access.expiration}") long expirationTimeMs) {
        super(secretKey, expirationTimeMs);
    }
}