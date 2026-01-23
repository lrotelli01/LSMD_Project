package largebeb.controllers;

import largebeb.dto.PaymentMethodRequestDTO; 
import largebeb.services.PaymentMethodService;
import largebeb.utilities.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    // Helper method to extract Bearer token
    private String extractToken(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    // ADD PAYMENT METHOD
    @PostMapping
    // FIX: Use <?> to allow returning a String message
    public ResponseEntity<?> addPaymentMethod(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody PaymentMethodRequestDTO request) {
        
        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            paymentMethodService.addPaymentMethod(token, request);
            // Now this works because the return type is <?>
            return ResponseEntity.ok("Payment method added successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // GET PAYMENT METHOD
    @GetMapping
    public ResponseEntity<?> getPaymentMethod(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            var method = paymentMethodService.getPaymentMethod(token);
            
            if (method == null) {
                return ResponseEntity.ok("No payment method saved.");
            }
            return ResponseEntity.ok(method);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // DELETE PAYMENT METHOD
    @DeleteMapping
    public ResponseEntity<?> deletePaymentMethod(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            paymentMethodService.deletePaymentMethod(token);
            return ResponseEntity.ok("Payment method removed successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal Error: " + e.getMessage());
        }
    }
}