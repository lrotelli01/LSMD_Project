package largebeb.controllers;

import largebeb.dto.*;
import largebeb.services.AnalyticsService;
import largebeb.services.ManagerPropertyService;
import largebeb.services.ManagerReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller for Manager-specific operations:
 * - Property CRUD
 * - Room CRUD  
 * - Analytics
 * - Reservations View
 * - Payment Status
 */
@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final ManagerPropertyService managerPropertyService;
    private final AnalyticsService analyticsService;
    private final ManagerReservationService managerReservationService;

    // ==================== PROPERTY MANAGEMENT ====================

    /**
     * Get all properties owned by the manager
     */
    @GetMapping("/properties")
    public ResponseEntity<?> getMyProperties(@RequestHeader("Authorization") String token) {
        try {
            List<PropertyResponseDTO> properties = managerPropertyService.getMyProperties(token);
            return ResponseEntity.ok(properties);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Add a new property
     */
    @PostMapping("/properties")
    public ResponseEntity<?> addProperty(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody PropertyRequestDTO request) {
        try {
            PropertyResponseDTO property = managerPropertyService.addProperty(token, request);
            return ResponseEntity.ok(property);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Modify property information
     */
    @PutMapping("/properties/{propertyId}")
    public ResponseEntity<?> modifyProperty(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @RequestBody PropertyRequestDTO request) {
        try {
            PropertyResponseDTO property = managerPropertyService.modifyProperty(token, propertyId, request);
            return ResponseEntity.ok(property);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Delete a property
     */
    @DeleteMapping("/properties/{propertyId}")
    public ResponseEntity<?> deleteProperty(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        try {
            managerPropertyService.deleteProperty(token, propertyId);
            return ResponseEntity.ok("Property deleted successfully.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // ==================== ROOM MANAGEMENT ====================

    /**
     * Get all rooms for a property
     */
    @GetMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<?> getRooms(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        try {
            List<RoomResponseDTO> rooms = managerPropertyService.getRooms(token, propertyId);
            return ResponseEntity.ok(rooms);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Add a room to a property
     */
    @PostMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<?> addRoom(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @Valid @RequestBody RoomRequestDTO request) {
        try {
            RoomResponseDTO room = managerPropertyService.addRoom(token, propertyId, request);
            return ResponseEntity.ok(room);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Modify room information
     */
    @PutMapping("/properties/{propertyId}/rooms/{roomId}")
    public ResponseEntity<?> modifyRoom(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @PathVariable String roomId,
            @RequestBody RoomRequestDTO request) {
        try {
            RoomResponseDTO room = managerPropertyService.modifyRoom(token, propertyId, roomId, request);
            return ResponseEntity.ok(room);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Delete a room from a property
     */
    @DeleteMapping("/properties/{propertyId}/rooms/{roomId}")
    public ResponseEntity<?> deleteRoom(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @PathVariable String roomId) {
        try {
            managerPropertyService.deleteRoom(token, propertyId, roomId);
            return ResponseEntity.ok("Room deleted successfully.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // ==================== ANALYTICS ====================

    /**
     * Get analytics for a specific property
     * Optional date filtering with startDate and endDate query params
     */
    @GetMapping("/analytics/property/{propertyId}")
    public ResponseEntity<?> getPropertyAnalytics(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            AnalyticsResponseDTO analytics = analyticsService.getPropertyAnalytics(token, propertyId, startDate, endDate);
            return ResponseEntity.ok(analytics);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get analytics for all properties owned by the manager
     */
    @GetMapping("/analytics/all")
    public ResponseEntity<?> getAllPropertiesAnalytics(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            List<AnalyticsResponseDTO> analytics = analyticsService.getAllPropertiesAnalytics(token, startDate, endDate);
            return ResponseEntity.ok(analytics);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get aggregated analytics across all properties
     */
    @GetMapping("/analytics/summary")
    public ResponseEntity<?> getAggregatedAnalytics(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            AnalyticsResponseDTO analytics = analyticsService.getAggregatedAnalytics(token, startDate, endDate);
            return ResponseEntity.ok(analytics);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // ==================== RESERVATIONS VIEW ====================

    /**
     * Get all reservations for manager's properties
     */
    @GetMapping("/reservations")
    public ResponseEntity<?> getAllMyReservations(@RequestHeader("Authorization") String token) {
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getAllMyReservations(token);
            return ResponseEntity.ok(reservations);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get reservations for a specific property
     */
    @GetMapping("/reservations/property/{propertyId}")
    public ResponseEntity<?> getPropertyReservations(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getPropertyReservations(token, propertyId);
            return ResponseEntity.ok(reservations);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get reservations by status (CONFIRMED, CANCELLED, COMPLETED)
     */
    @GetMapping("/reservations/status/{status}")
    public ResponseEntity<?> getReservationsByStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String status) {
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getReservationsByStatus(token, status);
            return ResponseEntity.ok(reservations);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get upcoming reservations
     */
    @GetMapping("/reservations/upcoming")
    public ResponseEntity<?> getUpcomingReservations(@RequestHeader("Authorization") String token) {
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getUpcomingReservations(token);
            return ResponseEntity.ok(reservations);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get current reservations (ongoing stays)
     */
    @GetMapping("/reservations/current")
    public ResponseEntity<?> getCurrentReservations(@RequestHeader("Authorization") String token) {
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getCurrentReservations(token);
            return ResponseEntity.ok(reservations);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // ==================== PAYMENT STATUS ====================

    /**
     * Get payment status for all rooms across all properties
     */
    @GetMapping("/payment-status")
    public ResponseEntity<?> getPaymentStatus(@RequestHeader("Authorization") String token) {
        try {
            List<RoomPaymentStatusDTO> paymentStatus = managerReservationService.getPaymentStatus(token);
            return ResponseEntity.ok(paymentStatus);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get payment status for a specific property
     */
    @GetMapping("/payment-status/property/{propertyId}")
    public ResponseEntity<?> getPropertyPaymentStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        try {
            RoomPaymentStatusDTO paymentStatus = managerReservationService.getPropertyPaymentStatus(token, propertyId);
            return ResponseEntity.ok(paymentStatus);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
