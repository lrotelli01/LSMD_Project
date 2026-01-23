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

    private static final Logger logger = LoggerFactory.getLogger(ManagerController.class);

    private final ManagerPropertyService managerPropertyService;
    private final AnalyticsService analyticsService;
    private final ManagerReservationService managerReservationService;

    // ==================== PROPERTY MANAGEMENT ====================

    /**
     * Get all properties owned by the manager
     */
    @GetMapping("/properties")
    public ResponseEntity<?> getMyProperties(@RequestHeader("Authorization") String token) {
        logger.info("[GET /api/manager/properties] Request to fetch all manager properties");
        
        try {
            List<PropertyResponseDTO> properties = managerPropertyService.getMyProperties(token);
            logger.info("[GET /api/manager/properties] SUCCESS - Returned {} properties", properties.size());
            return ResponseEntity.ok(properties);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/properties] FORBIDDEN - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[GET /api/manager/properties] BAD REQUEST - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid Request: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/properties] INTERNAL ERROR - {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    /**
     * Add a new property
     */
    @PostMapping("/properties")
    public ResponseEntity<?> addProperty(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody PropertyRequestDTO request) {
        
        logger.info("[POST /api/manager/properties] Request to add new property: name='{}', city='{}', country='{}'",
                request.getName(), request.getCity(), request.getCountry());
        
        // Input validation
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            logger.warn("[POST /api/manager/properties] VALIDATION ERROR - Property name is required");
            return ResponseEntity.badRequest().body("Validation Error: Property name is required");
        }
        if (request.getCity() == null || request.getCity().trim().isEmpty()) {
            logger.warn("[POST /api/manager/properties] VALIDATION ERROR - City is required");
            return ResponseEntity.badRequest().body("Validation Error: City is required");
        }
        if (request.getCountry() == null || request.getCountry().trim().isEmpty()) {
            logger.warn("[POST /api/manager/properties] VALIDATION ERROR - Country is required");
            return ResponseEntity.badRequest().body("Validation Error: Country is required");
        }
        
        try {
            PropertyResponseDTO property = managerPropertyService.addProperty(token, request);
            logger.info("[POST /api/manager/properties] SUCCESS - Property created with ID: {}", property.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(property);
            
        } catch (SecurityException e) {
            logger.warn("[POST /api/manager/properties] FORBIDDEN - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[POST /api/manager/properties] BAD REQUEST - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid Request: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[POST /api/manager/properties] INTERNAL ERROR - {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
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
        
        logger.info("[PUT /api/manager/properties/{}] Request to modify property", propertyId);
        
        if (propertyId == null || propertyId.trim().isEmpty()) {
            logger.warn("[PUT /api/manager/properties] VALIDATION ERROR - Property ID is required");
            return ResponseEntity.badRequest().body("Validation Error: Property ID is required in URL path");
        }
        
        try {
            PropertyResponseDTO property = managerPropertyService.modifyProperty(token, propertyId, request);
            logger.info("[PUT /api/manager/properties/{}] SUCCESS - Property updated", propertyId);
            return ResponseEntity.ok(property);
            
        } catch (SecurityException e) {
            logger.warn("[PUT /api/manager/properties/{}] FORBIDDEN - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[PUT /api/manager/properties/{}] NOT FOUND - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Not Found: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[PUT /api/manager/properties/{}] INTERNAL ERROR - {}", propertyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    /**
     * Delete a property
     */
    @DeleteMapping("/properties/{propertyId}")
    public ResponseEntity<?> deleteProperty(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        
        logger.info("[DELETE /api/manager/properties/{}] Request to delete property", propertyId);
        
        if (propertyId == null || propertyId.trim().isEmpty()) {
            logger.warn("[DELETE /api/manager/properties] VALIDATION ERROR - Property ID is required");
            return ResponseEntity.badRequest().body("Validation Error: Property ID is required in URL path");
        }
        
        try {
            managerPropertyService.deleteProperty(token, propertyId);
            logger.info("[DELETE /api/manager/properties/{}] SUCCESS - Property deleted", propertyId);
            return ResponseEntity.ok("Property deleted successfully.");
            
        } catch (SecurityException e) {
            logger.warn("[DELETE /api/manager/properties/{}] FORBIDDEN - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[DELETE /api/manager/properties/{}] NOT FOUND - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Not Found: " + e.getMessage());
                    
        } catch (IllegalStateException e) {
            logger.warn("[DELETE /api/manager/properties/{}] CONFLICT - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Conflict: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[DELETE /api/manager/properties/{}] INTERNAL ERROR - {}", propertyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
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
        
        logger.info("[GET /api/manager/properties/{}/rooms] Request to fetch rooms", propertyId);
        
        try {
            List<RoomResponseDTO> rooms = managerPropertyService.getRooms(token, propertyId);
            logger.info("[GET /api/manager/properties/{}/rooms] SUCCESS - Returned {} rooms", propertyId, rooms.size());
            return ResponseEntity.ok(rooms);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/properties/{}/rooms] FORBIDDEN - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[GET /api/manager/properties/{}/rooms] NOT FOUND - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Not Found: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/properties/{}/rooms] INTERNAL ERROR - {}", propertyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
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
        
        logger.info("[POST /api/manager/properties/{}/rooms] Request to add room: name='{}', type='{}', price={}",
                propertyId, request.getName(), request.getRoomType(), request.getPricePerNightAdults());
        
        // Input validation
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            logger.warn("[POST /api/manager/properties/{}/rooms] VALIDATION ERROR - Room name is required", propertyId);
            return ResponseEntity.badRequest().body("Validation Error: Room name is required");
        }
        if (request.getRoomType() == null || request.getRoomType().trim().isEmpty()) {
            logger.warn("[POST /api/manager/properties/{}/rooms] VALIDATION ERROR - Room type is required", propertyId);
            return ResponseEntity.badRequest().body("Validation Error: Room type is required (e.g., 'Single', 'Double', 'Suite')");
        }
        if (request.getCapacityAdults() == null || request.getCapacityAdults() <= 0) {
            logger.warn("[POST /api/manager/properties/{}/rooms] VALIDATION ERROR - Adult capacity must be positive", propertyId);
            return ResponseEntity.badRequest().body("Validation Error: Adult capacity must be a positive number");
        }
        if (request.getPricePerNightAdults() == null || request.getPricePerNightAdults() <= 0) {
            logger.warn("[POST /api/manager/properties/{}/rooms] VALIDATION ERROR - Price must be positive", propertyId);
            return ResponseEntity.badRequest().body("Validation Error: Price per night (adults) must be a positive number");
        }
        
        try {
            RoomResponseDTO room = managerPropertyService.addRoom(token, propertyId, request);
            logger.info("[POST /api/manager/properties/{}/rooms] SUCCESS - Room created with ID: {}", propertyId, room.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(room);
            
        } catch (SecurityException e) {
            logger.warn("[POST /api/manager/properties/{}/rooms] FORBIDDEN - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[POST /api/manager/properties/{}/rooms] NOT FOUND - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Not Found: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[POST /api/manager/properties/{}/rooms] INTERNAL ERROR - {}", propertyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
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
        
        logger.info("[PUT /api/manager/properties/{}/rooms/{}] Request to modify room", propertyId, roomId);
        
        try {
            RoomResponseDTO room = managerPropertyService.modifyRoom(token, propertyId, roomId, request);
            logger.info("[PUT /api/manager/properties/{}/rooms/{}] SUCCESS - Room updated", propertyId, roomId);
            return ResponseEntity.ok(room);
            
        } catch (SecurityException e) {
            logger.warn("[PUT /api/manager/properties/{}/rooms/{}] FORBIDDEN - {}", propertyId, roomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[PUT /api/manager/properties/{}/rooms/{}] NOT FOUND - {}", propertyId, roomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Not Found: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[PUT /api/manager/properties/{}/rooms/{}] INTERNAL ERROR - {}", propertyId, roomId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
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
        
        logger.info("[DELETE /api/manager/properties/{}/rooms/{}] Request to delete room", propertyId, roomId);
        
        try {
            managerPropertyService.deleteRoom(token, propertyId, roomId);
            logger.info("[DELETE /api/manager/properties/{}/rooms/{}] SUCCESS - Room deleted", propertyId, roomId);
            return ResponseEntity.ok("Room deleted successfully.");
            
        } catch (SecurityException e) {
            logger.warn("[DELETE /api/manager/properties/{}/rooms/{}] FORBIDDEN - {}", propertyId, roomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[DELETE /api/manager/properties/{}/rooms/{}] NOT FOUND - {}", propertyId, roomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Not Found: " + e.getMessage());
                    
        } catch (IllegalStateException e) {
            logger.warn("[DELETE /api/manager/properties/{}/rooms/{}] CONFLICT - {}", propertyId, roomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Conflict: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[DELETE /api/manager/properties/{}/rooms/{}] INTERNAL ERROR - {}", propertyId, roomId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    // ==================== ANALYTICS ====================

    /**
     * Get analytics for a specific property
     */
    @GetMapping("/analytics/property/{propertyId}")
    public ResponseEntity<?> getPropertyAnalytics(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        logger.info("[GET /api/manager/analytics/property/{}] Request for analytics - startDate={}, endDate={}",
                propertyId, startDate, endDate);
        
        // Validate date range
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            logger.warn("[GET /api/manager/analytics/property/{}] VALIDATION ERROR - startDate cannot be after endDate", propertyId);
            return ResponseEntity.badRequest().body("Validation Error: startDate cannot be after endDate");
        }
        
        try {
            AnalyticsResponseDTO analytics = analyticsService.getPropertyAnalytics(token, propertyId, startDate, endDate);
            logger.info("[GET /api/manager/analytics/property/{}] SUCCESS - Analytics retrieved", propertyId);
            return ResponseEntity.ok(analytics);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/analytics/property/{}] FORBIDDEN - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[GET /api/manager/analytics/property/{}] NOT FOUND - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Not Found: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/analytics/property/{}] INTERNAL ERROR - {}", propertyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
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
        
        logger.info("[GET /api/manager/analytics/all] Request for all properties analytics - startDate={}, endDate={}",
                startDate, endDate);
        
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            logger.warn("[GET /api/manager/analytics/all] VALIDATION ERROR - startDate cannot be after endDate");
            return ResponseEntity.badRequest().body("Validation Error: startDate cannot be after endDate");
        }
        
        try {
            List<AnalyticsResponseDTO> analytics = analyticsService.getAllPropertiesAnalytics(token, startDate, endDate);
            logger.info("[GET /api/manager/analytics/all] SUCCESS - Retrieved analytics for {} properties", analytics.size());
            return ResponseEntity.ok(analytics);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/analytics/all] FORBIDDEN - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/analytics/all] INTERNAL ERROR - {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
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
        
        logger.info("[GET /api/manager/analytics/summary] Request for aggregated analytics - startDate={}, endDate={}",
                startDate, endDate);
        
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            logger.warn("[GET /api/manager/analytics/summary] VALIDATION ERROR - startDate cannot be after endDate");
            return ResponseEntity.badRequest().body("Validation Error: startDate cannot be after endDate");
        }
        
        try {
            AnalyticsResponseDTO analytics = analyticsService.getAggregatedAnalytics(token, startDate, endDate);
            logger.info("[GET /api/manager/analytics/summary] SUCCESS - Aggregated analytics retrieved");
            return ResponseEntity.ok(analytics);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/analytics/summary] FORBIDDEN - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/analytics/summary] INTERNAL ERROR - {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    // ==================== RESERVATIONS VIEW ====================

    /**
     * Get all reservations for manager's properties
     */
    @GetMapping("/reservations")
    public ResponseEntity<?> getAllMyReservations(@RequestHeader("Authorization") String token) {
        logger.info("[GET /api/manager/reservations] Request to fetch all reservations");
        
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getAllMyReservations(token);
            logger.info("[GET /api/manager/reservations] SUCCESS - Returned {} reservations", reservations.size());
            return ResponseEntity.ok(reservations);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/reservations] FORBIDDEN - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/reservations] INTERNAL ERROR - {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    /**
     * Get reservations for a specific property
     */
    @GetMapping("/reservations/property/{propertyId}")
    public ResponseEntity<?> getPropertyReservations(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        
        logger.info("[GET /api/manager/reservations/property/{}] Request for property reservations", propertyId);
        
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getPropertyReservations(token, propertyId);
            logger.info("[GET /api/manager/reservations/property/{}] SUCCESS - Returned {} reservations", propertyId, reservations.size());
            return ResponseEntity.ok(reservations);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/reservations/property/{}] FORBIDDEN - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[GET /api/manager/reservations/property/{}] NOT FOUND - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Not Found: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/reservations/property/{}] INTERNAL ERROR - {}", propertyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    /**
     * Get reservations by status (CONFIRMED, CANCELLED, COMPLETED)
     */
    @GetMapping("/reservations/status/{status}")
    public ResponseEntity<?> getReservationsByStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String status) {
        
        logger.info("[GET /api/manager/reservations/status/{}] Request for reservations by status", status);
        
        // Validate status
        if (!status.equalsIgnoreCase("CONFIRMED") && 
            !status.equalsIgnoreCase("CANCELLED") && 
            !status.equalsIgnoreCase("COMPLETED") &&
            !status.equalsIgnoreCase("PENDING_PAYMENT")) {
            logger.warn("[GET /api/manager/reservations/status/{}] VALIDATION ERROR - Invalid status", status);
            return ResponseEntity.badRequest()
                    .body("Validation Error: Status must be one of: CONFIRMED, CANCELLED, COMPLETED, PENDING_PAYMENT");
        }
        
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getReservationsByStatus(token, status);
            logger.info("[GET /api/manager/reservations/status/{}] SUCCESS - Returned {} reservations", status, reservations.size());
            return ResponseEntity.ok(reservations);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/reservations/status/{}] FORBIDDEN - {}", status, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/reservations/status/{}] INTERNAL ERROR - {}", status, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    /**
     * Get upcoming reservations
     */
    @GetMapping("/reservations/upcoming")
    public ResponseEntity<?> getUpcomingReservations(@RequestHeader("Authorization") String token) {
        logger.info("[GET /api/manager/reservations/upcoming] Request for upcoming reservations");
        
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getUpcomingReservations(token);
            logger.info("[GET /api/manager/reservations/upcoming] SUCCESS - Returned {} upcoming reservations", reservations.size());
            return ResponseEntity.ok(reservations);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/reservations/upcoming] FORBIDDEN - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/reservations/upcoming] INTERNAL ERROR - {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    /**
     * Get current reservations (ongoing stays)
     */
    @GetMapping("/reservations/current")
    public ResponseEntity<?> getCurrentReservations(@RequestHeader("Authorization") String token) {
        logger.info("[GET /api/manager/reservations/current] Request for current ongoing reservations");
        
        try {
            List<ManagerReservationDTO> reservations = managerReservationService.getCurrentReservations(token);
            logger.info("[GET /api/manager/reservations/current] SUCCESS - Returned {} current reservations", reservations.size());
            return ResponseEntity.ok(reservations);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/reservations/current] FORBIDDEN - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/reservations/current] INTERNAL ERROR - {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    // ==================== PAYMENT STATUS ====================

    /**
     * Get payment status for all rooms across all properties
     */
    @GetMapping("/payment-status")
    public ResponseEntity<?> getPaymentStatus(@RequestHeader("Authorization") String token) {
        logger.info("[GET /api/manager/payment-status] Request for all rooms payment status");
        
        try {
            List<RoomPaymentStatusDTO> paymentStatus = managerReservationService.getPaymentStatus(token);
            logger.info("[GET /api/manager/payment-status] SUCCESS - Returned payment status for {} properties", paymentStatus.size());
            return ResponseEntity.ok(paymentStatus);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/payment-status] FORBIDDEN - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/payment-status] INTERNAL ERROR - {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }

    /**
     * Get payment status for a specific property
     */
    @GetMapping("/payment-status/property/{propertyId}")
    public ResponseEntity<?> getPropertyPaymentStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        
        logger.info("[GET /api/manager/payment-status/property/{}] Request for property payment status", propertyId);
        
        try {
            RoomPaymentStatusDTO paymentStatus = managerReservationService.getPropertyPaymentStatus(token, propertyId);
            logger.info("[GET /api/manager/payment-status/property/{}] SUCCESS - Payment status retrieved", propertyId);
            return ResponseEntity.ok(paymentStatus);
            
        } catch (SecurityException e) {
            logger.warn("[GET /api/manager/payment-status/property/{}] FORBIDDEN - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: " + e.getMessage());
                    
        } catch (IllegalArgumentException e) {
            logger.error("[GET /api/manager/payment-status/property/{}] NOT FOUND - {}", propertyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Not Found: " + e.getMessage());
                    
        } catch (Exception e) {
            logger.error("[GET /api/manager/payment-status/property/{}] INTERNAL ERROR - {}", propertyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: An unexpected error occurred");
        }
    }
}
