package largebeb.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import largebeb.dto.DeleteAccountResponseDTO; 
import largebeb.services.DeleteAccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Account", description = "User account management including account deletion")
public class DeleteAccountController {

    private final DeleteAccountService deleteAccountService;

    @DeleteMapping("/delete")
    public ResponseEntity<DeleteAccountResponseDTO> deleteAccount(HttpServletRequest request) {
        
        // Extract the Authorization header
        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // Remove "Bearer " prefix
        }

        // Validate that the token exists
        if (token == null) {
            return ResponseEntity.badRequest()
                    .body(DeleteAccountResponseDTO.builder()
                            .message("Authorization token is missing.")
                            .success(false)
                            .build());
        }

        try {
            // Call the service to delete the account
            deleteAccountService.deleteAccount(token);

            // Return success response
            return ResponseEntity.ok(DeleteAccountResponseDTO.builder()
                    .message("Account deleted successfully. We are sorry to see you go!")
                    .success(true)
                    .build());

        } catch (IllegalArgumentException e) {
            // Handle cases where token is invalid or user is not found
            return ResponseEntity.badRequest()
                    .body(DeleteAccountResponseDTO.builder()
                            .message("Error: " + e.getMessage())
                            .success(false)
                            .build());
            
        } catch (Exception e) {
            // Handle unexpected server errors
            return ResponseEntity.internalServerError()
                    .body(DeleteAccountResponseDTO.builder()
                            .message("An unexpected error occurred while deleting the account.")
                            .success(false)
                            .build());
        }
    }
}