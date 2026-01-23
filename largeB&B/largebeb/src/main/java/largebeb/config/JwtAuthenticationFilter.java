package largebeb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import largebeb.repository.UserRepository;
import largebeb.model.RegisteredUser;
import largebeb.utilities.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        String requestURI = request.getRequestURI();

        // Logga solo le richieste API per non intasare la console con file statici/swagger
        if (requestURI.startsWith("/api/")) {
            logger.info(">>> SECURITY FILTER: Richiesta in ingresso verso {}", requestURI);
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    String userId = jwtUtil.getUserIdFromToken(token);
                    logger.info(">>> TOKEN VALIDO. UserID estratto: {}", userId);
                    
                    // Cerco l'utente fresco dal Database
                    RegisteredUser user = userRepository.findById(userId).orElse(null);

                    if (user != null) {
                        String role = user.getRole(); 
                        logger.info(">>> UTENTE TROVATO: {} | Ruolo: {}", user.getEmail(), role);
                        
                        // Assegno autorità ROLE_...
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

                        UsernamePasswordAuthenticationToken authToken = 
                            new UsernamePasswordAuthenticationToken(
                                user.getEmail(), 
                                null, 
                                Collections.singletonList(authority)
                            );
                        
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        logger.info(">>> AUTENTICAZIONE COMPLETATA con successo.");

                    } else {
                        logger.error("!!! ERRORE CRITICO: UserID {} nel token NON ESISTE nel Database! Utente cancellato?", userId);
                    }
                } else {
                    logger.warn("!!! TOKEN NON VALIDO (firma errata o scaduto)");
                }
            } catch (Exception e) {
                logger.error("!!! ECCEZIONE nel filtro: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else {
            if (requestURI.startsWith("/api/") && !requestURI.contains("/auth/")) {
                logger.warn("!!! Header Authorization mancante o non inizia con 'Bearer '");
            }
        }
        
        filterChain.doFilter(request, response);
    }
}