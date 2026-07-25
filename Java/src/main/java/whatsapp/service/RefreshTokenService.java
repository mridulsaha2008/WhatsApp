package whatsapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService extends TokenService {
    public RefreshTokenService(
            @Value("${jwt.refresh.secret}") String secretKey,
            @Value("${jwt.refresh.expiration}") long expirationTimeMs) {
        super(secretKey, expirationTimeMs);
    }
}