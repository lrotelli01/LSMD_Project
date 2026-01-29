package largebeb.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import largebeb.dto.LoginRequestDTO;
import largebeb.dto.LoginResponseDTO;
import largebeb.services.LoginService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User authentication: login with JWT token generation")
public class LoginController {

    // Inject the SERVICE
    private final LoginService loginService;

    // Dependency Injection: We ask Spring to give us the LoginService
    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login") // Handles POST requests to /login
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        
        // Input check
        if (loginRequest.getEmail() == null || loginRequest.getPassword() == null) {
            return ResponseEntity.badRequest().body("Email and password are required");
        }

        try {
            // Delegate to Service for authentication
            LoginResponseDTO response = loginService.authenticate(loginRequest);

            // Success - return 200 OK with response body
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Error handling  for invalid credentials
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
            
        } catch (Exception e) {
            // Generic error handling: 500 Internal Server Error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred");
        }
    }
}