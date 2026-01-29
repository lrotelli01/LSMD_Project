package largebeb.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import largebeb.dto.LogoutResponseDTO;
import largebeb.services.LogoutService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication: secure logout with token blacklisting")
public class LogoutController {

    private final LogoutService logoutService;

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponseDTO> logout(HttpServletRequest request) {
        // Extract token from Authorization header
        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // Remove "Bearer " prefix
        }

        // Check if token exists
        if (token == null) {
            return ResponseEntity.badRequest()
                    .body(LogoutResponseDTO.builder()
                            .message("Authorization token is missing.")
                            .success(false)
                            .build());
        }

        try {
            // Call Service
            logoutService.performLogout(token);

            // Return Success
            return ResponseEntity.ok(LogoutResponseDTO.builder()
                    .message("Logout successful.")
                    .success(true)
                    .build());

        } catch (IllegalArgumentException e) {
            // Token invalid or expired
            return ResponseEntity.badRequest()
                    .body(LogoutResponseDTO.builder()
                            .message("Logout failed: " + e.getMessage())
                            .success(false)
                            .build());
        } catch (Exception e) {
            // Generic server error
            return ResponseEntity.internalServerError()
                    .body(LogoutResponseDTO.builder()
                            .message("Internal server error during logout.")
                            .success(false)
                            .build());
        }
    }
}   