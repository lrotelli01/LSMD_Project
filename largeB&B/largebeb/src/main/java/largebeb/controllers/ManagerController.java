package largebeb.controllers;

import largebeb.dto.*;
import largebeb.services.AnalyticsService;
import largebeb.services.ManagerPropertyService;
import largebeb.services.ManagerReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerController {

    private static final Logger logger = LoggerFactory.getLogger(ManagerController.class);

    private final ManagerPropertyService managerPropertyService;
    private final AnalyticsService analyticsService;
    private final ManagerReservationService managerReservationService;

    // ==================== PROPERTY MANAGEMENT ====================

    @GetMapping("/properties")
    public ResponseEntity<?> getMyProperties(@RequestHeader("Authorization") String token) {
        try {
            List<PropertyResponseDTO> properties = managerPropertyService.getMyProperties(token);
            return ResponseEntity.ok(properties);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @PostMapping("/properties")
    public ResponseEntity<?> addProperty(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody PropertyRequestDTO request) {
        
        logger.info("[POST] Adding property: {}", request.getName());
        try {
            PropertyResponseDTO property = managerPropertyService.addProperty(token, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(property);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @PutMapping("/properties/{propertyId}")
    public ResponseEntity<?> modifyProperty(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @Valid @RequestBody PropertyRequestDTO request) {
        try {
            PropertyResponseDTO property = managerPropertyService.modifyProperty(token, propertyId, request);
            return ResponseEntity.ok(property);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @DeleteMapping("/properties/{propertyId}")
    public ResponseEntity<?> deleteProperty(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        try {
            managerPropertyService.deleteProperty(token, propertyId);
            return ResponseEntity.ok("Property deleted successfully.");
        } catch (Exception e) {
            return handleException(e);
        }
    }

    // ==================== ROOM MANAGEMENT ====================

    @GetMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<?> getRooms(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        try {
            List<RoomResponseDTO> rooms = managerPropertyService.getRooms(token, propertyId);
            return ResponseEntity.ok(rooms);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @PostMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<?> addRoom(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @Valid @RequestBody RoomRequestDTO request) {
        
        logger.info("[POST] Adding room to property {}", propertyId);
        try {
            RoomResponseDTO room = managerPropertyService.addRoom(token, propertyId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(room);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @PutMapping("/properties/{propertyId}/rooms/{roomId}")
    public ResponseEntity<?> modifyRoom(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @PathVariable String roomId,
            @Valid @RequestBody RoomRequestDTO request) {
        try {
            RoomResponseDTO room = managerPropertyService.modifyRoom(token, propertyId, roomId, request);
            return ResponseEntity.ok(room);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @DeleteMapping("/properties/{propertyId}/rooms/{roomId}")
    public ResponseEntity<?> deleteRoom(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @PathVariable String roomId) {
        try {
            managerPropertyService.deleteRoom(token, propertyId, roomId);
            return ResponseEntity.ok("Room deleted successfully.");
        } catch (Exception e) {
            return handleException(e);
        }
    }

    // ==================== ANALYTICS ====================

    @GetMapping("/analytics/property/{propertyId}")
    public ResponseEntity<?> getPropertyAnalytics(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body("Validation Error: startDate cannot be after endDate");
        }
        try {
            return ResponseEntity.ok(analyticsService.getPropertyAnalytics(token, propertyId, startDate, endDate));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @GetMapping("/analytics/all")
    public ResponseEntity<?> getAllPropertiesAnalytics(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body("Validation Error: startDate cannot be after endDate");
        }
        try {
            return ResponseEntity.ok(analyticsService.getAllPropertiesAnalytics(token, startDate, endDate));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @GetMapping("/analytics/summary")
    public ResponseEntity<?> getAggregatedAnalytics(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body("Validation Error: startDate cannot be after endDate");
        }
        try {
            return ResponseEntity.ok(analyticsService.getAggregatedAnalytics(token, startDate, endDate));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    // ==================== RESERVATIONS VIEW ====================

    @GetMapping("/reservations")
    public ResponseEntity<?> getAllMyReservations(@RequestHeader("Authorization") String token) {
        try {
            return ResponseEntity.ok(managerReservationService.getAllMyReservations(token));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @GetMapping("/reservations/property/{propertyId}")
    public ResponseEntity<?> getPropertyReservations(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        try {
            return ResponseEntity.ok(managerReservationService.getPropertyReservations(token, propertyId));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @GetMapping("/reservations/status/{status}")
    public ResponseEntity<?> getReservationsByStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String status) {
        try {
            return ResponseEntity.ok(managerReservationService.getReservationsByStatus(token, status));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @GetMapping("/reservations/upcoming")
    public ResponseEntity<?> getUpcomingReservations(@RequestHeader("Authorization") String token) {
        try {
            return ResponseEntity.ok(managerReservationService.getUpcomingReservations(token));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @GetMapping("/reservations/current")
    public ResponseEntity<?> getCurrentReservations(@RequestHeader("Authorization") String token) {
        try {
            return ResponseEntity.ok(managerReservationService.getCurrentReservations(token));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    // ==================== PAYMENT STATUS ====================

    @GetMapping("/payment-status")
    public ResponseEntity<?> getPaymentStatus(@RequestHeader("Authorization") String token) {
        try {
            return ResponseEntity.ok(managerReservationService.getPaymentStatus(token));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @GetMapping("/payment-status/property/{propertyId}")
    public ResponseEntity<?> getPropertyPaymentStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        try {
            return ResponseEntity.ok(managerReservationService.getPropertyPaymentStatus(token, propertyId));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    // ==================== EXCEPTION HANDLER HELPER ====================
    
    private ResponseEntity<?> handleException(Exception e) {
        if (e instanceof SecurityException) {
            logger.warn("Access Denied: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied: " + e.getMessage());
        } else if (e instanceof IllegalArgumentException) {
            logger.error("Bad Request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: " + e.getMessage());
        } else if (e instanceof IllegalStateException) {
             logger.warn("Conflict: {}", e.getMessage());
             return ResponseEntity.status(HttpStatus.CONFLICT).body("Conflict: " + e.getMessage());
        } else {
            logger.error("Internal Error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred");
        }
    }
}