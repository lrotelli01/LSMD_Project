package largebeb.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import largebeb.dto.PaymentRequestDTO;
import largebeb.dto.ReservationRequestDTO;
import largebeb.dto.ReservationResponseDTO;
import largebeb.services.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Booking management: initiate, confirm payment, modify, and cancel reservations")
public class ReservationController {

    private final ReservationService reservationService;

    // Helper method to extract Bearer token
    private String extractToken(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    // GET RESERVATIONS HISTORY
    @GetMapping("/my-reservations")
    public ResponseEntity<?> getUserReservations(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            List<largebeb.dto.ReservationResponseDTO> reservations = reservationService.getUserReservations(token);
            return ResponseEntity.ok(reservations);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // INITIATE RESERVATION
    @PostMapping("/initiate")
    public ResponseEntity<?> initiateReservation(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody @Valid ReservationRequestDTO requestDTO) {
        
        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            ReservationResponseDTO response = reservationService.initiateReservation(token, requestDTO);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 409 Conflict (Occupied) or 400 Bad Request (Not found/Capacity)
            return ResponseEntity.status(409).body(e.getMessage()); 
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/temp/{tempReservationId}")
        public ResponseEntity<?> removeTemporaryReservation(
                @RequestHeader("Authorization") String authHeader,
                @PathVariable String tempReservationId) {

            String token = extractToken(authHeader);
            if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

            try {
                reservationService.removeTemporaryReservation(token, tempReservationId);
                return ResponseEntity.ok("Temporary reservation lock released successfully.");
            } catch (SecurityException e) {
                return ResponseEntity.status(403).body(e.getMessage());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Error: " + e.getMessage());
            }
    }

    // CONFIRM PAYMENT
    @PostMapping("/confirm-payment")
    public ResponseEntity<?> confirmPayment( @RequestHeader("Authorization") String authHeader,
        @RequestBody @Valid PaymentRequestDTO paymentRequest) {
        String token = extractToken(authHeader);
        if (token == null) 
            return ResponseEntity.badRequest().body("Invalid Token");
        try {
            ReservationResponseDTO response = reservationService.confirmPayment(token,paymentRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage()); 
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // MODIFY RESERVATION
    @PutMapping("/{reservationId}")
    public ResponseEntity<?> modifyReservation(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String reservationId,
            @RequestBody @Valid ReservationRequestDTO requestDTO) {

        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            ReservationResponseDTO response = reservationService.modifyReservation(token, reservationId, requestDTO);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // CANCEL RESERVATION
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<?> cancelReservation(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String reservationId) {

        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            reservationService.cancelReservation(token, reservationId);
            return ResponseEntity.ok("Reservation cancelled successfully. Refund processed.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}