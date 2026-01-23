package largebeb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import largebeb.utilities.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    // Constructor to inject JwtUtil
    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Get the Authorization Header from the request
        String authHeader = request.getHeader("Authorization");

        // Check if the header contains a Bearer token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // Remove "Bearer " prefix
            try {
                // Validate the token using JwtUtil
                if (jwtUtil.validateToken(token)) {
                    String email = jwtUtil.getEmailFromToken(token);

                    // Set the authentication in the Security Context
                    // We use an empty list for roles/authorities for simplicity
                    UsernamePasswordAuthenticationToken authToken = 
                        new UsernamePasswordAuthenticationToken(email, null, new ArrayList<>());
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // This tells Spring: "This user is authenticated"
                    // Once this is set, Controllers can access the current user's email
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } 
                else {
                    // Token is present but isn't valid
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT Token");
                    return; // Then chain stop
                }
            }   
             catch (Exception e) {
                // If JwtUtil throw exceptions (es. ExpiredJwtException)
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT Token Error: " + e.getMessage());
                return; // Then chain stop
            }

        }
        // Continue with the next filter in the chain or the request processing
        filterChain.doFilter(request, response);
    }
}