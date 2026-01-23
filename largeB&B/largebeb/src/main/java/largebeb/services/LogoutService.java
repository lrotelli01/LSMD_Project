package largebeb.services;

import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutService {

    // Direct dependency on JwtUtil (which handles Redis)
    private final JwtUtil jwtUtil;

    public void performLogout(String token) {
        // Clean the token (remove "Bearer " prefix if present)
        String cleanToken = (token != null && token.startsWith("Bearer ")) 
                            ? token.substring(7) 
                            : token;

        // Validate the token before blacklisting
        // We check if it's a valid signed JWT and not already expired
        if (cleanToken != null && jwtUtil.validateToken(cleanToken)) {
            
            // Blacklist the token directly via JwtUtil
            // This calculates the TTL and saves the key in Redis
            jwtUtil.blacklistToken(cleanToken);
            
        } else {
            // Handle invalid token case
            // If the token is already expired, we technically don't need to blacklist it,
            // but throwing an exception alerts the client that the session was already invalid.
            throw new IllegalArgumentException("Token is invalid, already expired, or tampered with.");
        }
    }
}