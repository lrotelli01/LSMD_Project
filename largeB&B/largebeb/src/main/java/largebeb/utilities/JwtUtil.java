package largebeb.utilities;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // Importante!
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

/*
 Utility class for handling JSON Web Tokens (JWT).
 Responsible for generating, validating, and parsing tokens, 
 as well as handling token blacklisting via Redis.
*/
@Component
public class JwtUtil {

    /*
     Injected from application.properties.
     This allows changing the key without recompiling the code.
    */
    @Value("${jwt.secret}")
    private String secretKey;
    
    // Injected expiration time (defaulting to 1 hour if not found in properties)
    @Value("${jwt.expiration:3600000}")
    private long expirationTime;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Removes the "Bearer " prefix from the token string if it exists
    private String cleanToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }

    /*
     Generates a new JWT for a specific user.
     It includes custom claims for userId and username, sets the email as the subject,
     and signs the token with the secret key.
    */
    public String generateToken(String userId, String email, String username) {
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("username", username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime)) // Usa la variabile iniettata
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Extracts the email (Subject) from the token
    public String getEmailFromToken(String token) {
        // Remove "Bearer " prefix if present to ensure the token is valid for parsing
        String cleanedToken = cleanToken(token);
        return getClaims(cleanedToken).getSubject();
    }

    // Extract the userId from the body
    public String getUserIdFromToken(String token) {
        // Remove "Bearer " prefix if present to ensure the token is valid for parsing
        String cleanedToken = cleanToken(token);
        
        // Retrieve the body (payload) of the token and get the "userId" claim
        return getClaims(cleanedToken).get("userId", String.class);
    }

    // Extracts the username custom claim from the token.
    public String getUsernameFromToken(String token) {
        String cleanedToken = cleanToken(token);
        return getClaims(cleanedToken).get("username", String.class);
    }

    /*
     Validates the token integrity and status.
     It checks the cryptographic signature, verifies if the token has expired,
     and confirms it has not been blacklisted in Redis.
    */
    public boolean validateToken(String token) {
        try {
            String cleanedToken = cleanToken(token);

            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(cleanedToken);
            
            if (isTokenBlacklisted(cleanedToken)) {
                System.out.println("DEBUG: Token is in Redis Blacklist.");
                return false;
            }
            
            return true;
            
        } catch (SignatureException e) {
            System.out.println("DEBUG: Invalid JWT Signature: " + e.getMessage());
        } catch (MalformedJwtException e) {
            System.out.println("DEBUG: Malformed JWT Token: " + e.getMessage());
        } catch (ExpiredJwtException e) {
            System.out.println("DEBUG: Expired JWT Token: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("DEBUG: Generic Token Validation Error: " + e.getMessage());
        }
        return false;
    }

    /*
     Adds the token to the Redis blacklist to invalidate it.
     The duration in Redis is calculated based on the remaining time until the token naturally expires.
    */
    public void blacklistToken(String token) {
        try {
            String cleanedToken = cleanToken(token);
            
            Claims claims = getClaims(cleanedToken);
            long tokenExpiration = claims.getExpiration().getTime(); // Rinomino per chiarezza vs variabile di classe
            long currentTime = System.currentTimeMillis();
            
            long ttl = tokenExpiration - currentTime;

            if (ttl > 0) {
                redisTemplate.opsForValue().set("blacklist:" + cleanedToken, "true", ttl, TimeUnit.MILLISECONDS);
                System.out.println("DEBUG: Token added to blacklist for " + ttl + "ms");
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Could not blacklist token: " + e.getMessage());
        }
    }

    // Internal helper to check if the token key exists in Redis
    private boolean isTokenBlacklisted(String token) {
        try {
            Boolean exists = redisTemplate.hasKey("blacklist:" + token);
            return exists != null && exists;
        } catch (Exception e) {
            System.out.println("DEBUG: Redis Connection Error: " + e.getMessage());
            return false; 
        }
    }

    // Parses the JWT to extract all claims (payload)
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Decodes the secret key for signing and verifying
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}