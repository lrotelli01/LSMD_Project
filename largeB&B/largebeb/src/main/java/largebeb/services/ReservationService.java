package largebeb.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import largebeb.dto.ReservationRequestDTO;
import largebeb.dto.ReservationResponseDTO;
import largebeb.model.Property;
import largebeb.model.RegisteredUser;
import largebeb.model.Reservation;
import largebeb.model.Room;
import largebeb.repository.PropertyRepository;
import largebeb.repository.ReservationGraphRepository;
import largebeb.repository.ReservationRepository;
import largebeb.repository.UserRepository;
import largebeb.utilities.JwtUtil;
import largebeb.utilities.RatingStats;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import largebeb.model.Customer;
import largebeb.utilities.PaymentMethod;
import largebeb.dto.PaymentRequestDTO;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final ReservationGraphRepository reservationGraphRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;

    private static final String REDIS_PREFIX = "temp_res:";

    // INITIATE RESERVATION

    public ReservationResponseDTO initiateReservation(String token, ReservationRequestDTO request) {
        
        // Check if CheckIn is equal or after CheckOut
        if (!request.getCheckIn().isBefore(request.getCheckOut())) {
            throw new IllegalArgumentException("Invalid dates: Check-out date must be strictly after Check-in date.");
        }
        
        // Check if CheckIn is in the past
        if (request.getCheckIn().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Invalid dates: Check-in date cannot be in the past.");
        }

        // RESOLVE NAMES TO ENTITIES
        
        // Find Property by Name
        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + request.getPropertyId()));

        // Identify User and Check Role
        String userId = jwtUtil.getUserIdFromToken(token);
        
        RegisteredUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Only CUSTOMER can make reservations
        if (!"CUSTOMER".equalsIgnoreCase(user.getRole())) {
            throw new SecurityException("Access Denied: Only customers can initiate reservations.");
        }

        // Find Room by Name AND Property ID (ensures uniqueness)
        Room room = property.getRooms().stream()
        .filter(r -> r.getId().trim().equals(request.getRoomId().trim()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Room not found with id: " + request.getRoomId() + " in this property"));

        // VALIDATIONS

        if (!"available".equalsIgnoreCase(room.getStatus())) {
            throw new IllegalStateException("Room is currently under maintenance or unavailable.");
        }

        // Validate Occupancy (Using correct field names)
        validateOccupancy(room, request.getAdults(), request.getChildren());

        // Check MongoDB for confirmed bookings (Using ID)
        List<Reservation> confirmedOverlaps = reservationRepository.findOverlappingReservations(
                room.getId(), request.getCheckIn(), request.getCheckOut());
        
        if (!confirmedOverlaps.isEmpty()) {
            throw new IllegalStateException("Room is already booked for these dates.");
        }

        // Check Redis for temporary locks
        Set<String> keys = redisTemplate.keys(REDIS_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                try {
                    Reservation pending = objectMapper.convertValue(redisTemplate.opsForValue().get(key), Reservation.class);
                    // Check overlap using the resolved Room ID
                    if (pending != null && pending.getRoomId().equals(room.getId()) &&
                            isOverlapping(pending.getDates(), request.getCheckIn(), request.getCheckOut())) {
                        throw new IllegalStateException("Room is currently being paid for by another user. Try again in 15 mins.");
                    }
                }
                catch (IllegalStateException e) {
                    throw e; 
                }
                catch (Exception e) {
                    System.out.println("READING ERROR FOR KEY: "+ key);
                    e.printStackTrace();
                }
            }
        }

        // CREATE TEMPORARY RESERVATION

        String tempId = UUID.randomUUID().toString();

        Reservation tempReservation = Reservation.builder()
                .id(tempId)
                .userId(userId)
                .roomId(room.getId())
                .adults(request.getAdults())
                .children(request.getChildren())
                .dates(new Reservation.ReservationDates(request.getCheckIn(), request.getCheckOut()))
                .status("PENDING_PAYMENT")
                .build();

        redisTemplate.opsForValue().set(REDIS_PREFIX + tempId, tempReservation, 15, TimeUnit.MINUTES);

        double estimatedPrice = calculateTotalPrice(room, request.getCheckIn(), request.getCheckOut(), request.getAdults(), request.getChildren());

        // Map to DTO passing the room and the success message
        return mapToDTO(tempReservation, room, "Room locked for 15 minutes. Total to pay: €" + String.format("%.2f", estimatedPrice));
    }

    // REMOVE TEMPORARY RESERVATION
    public void removeTemporaryReservation(String token, String tempReservationId) {
        String redisKey = REDIS_PREFIX + tempReservationId;

        // Check if the temporary reservation exists in Redis
        Reservation tempReservation = (Reservation) redisTemplate.opsForValue().get(redisKey);
        
        if (tempReservation == null) {
            // Already expired or doesn't exist, simply return
            return;
        }

        // Validate Ownership
        String userId = jwtUtil.getUserIdFromToken(token);
        if (!tempReservation.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to remove this reservation lock.");
        }

        // Delete from Redis
        redisTemplate.delete(redisKey);
        System.out.println("Temporary reservation " + tempReservationId + " removed from Redis.");
    }
    
    // CONFIRM PAYMENT
    public ReservationResponseDTO confirmPayment(String token, PaymentRequestDTO paymentRequest) {
        if (paymentRequest.getTempReservationId() == null || paymentRequest.getTempReservationId().isEmpty()) {
            throw new IllegalArgumentException("Temporary Reservation ID is required to confirm payment.");
        }   
        String redisKey = REDIS_PREFIX + paymentRequest.getTempReservationId();

        // Check if the temporary reservation exists in Redis
        Reservation tempReservation = (Reservation) redisTemplate.opsForValue().get(redisKey);
        if (tempReservation == null) {
            throw new IllegalArgumentException("Reservation session expired. Please search again.");
        }

        // CHECK FOR PAYMENT METHOD
        // Retrieve the user to verify if they have a saved payment method

        // Use RegisteredUser first to check the role safely
        String userId = jwtUtil.getUserIdFromToken(token);
        
        RegisteredUser genericUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Only CUSTOMER can make reservations
        if (!"CUSTOMER".equalsIgnoreCase(genericUser.getRole())) {
            throw new SecurityException("Access Denied: Only customers can initiate reservations.");
        }
        Customer user = (Customer) genericUser;
        
        // Get the payment method
        PaymentMethod method = user.getPaymentMethod();
        if (method == null) {
            throw new IllegalStateException("No payment method found. Please add one before confirming payment.");
        }
        String gatewayToken = method.getGatewayToken(); // This is our secure token

        // Resolve Room and Property for price calculation
        Property property = propertyRepository.findByRoomsId(tempReservation.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Property not found for this room"));
        
        Room room = property.getRooms().stream()
                .filter(r -> r.getId().equals(tempReservation.getRoomId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        double amountToPay = calculateTotalPrice(
                room,
                tempReservation.getDates().getCheckIn(),
                tempReservation.getDates().getCheckOut(),
                tempReservation.getAdults(),
                tempReservation.getChildren()
        );

        // SIMULATE BANK CHARGE USING THE TOKEN
        // We pass the gatewayToken instead of card numbers
        boolean paymentSuccess = simulateBankChargeWithToken(user.getUsername(), amountToPay, gatewayToken);
        
        if (!paymentSuccess) {
            throw new IllegalStateException("Payment declined by bank using the saved payment method.");
        }

        // Finalize Reservation
        tempReservation.setStatus("CONFIRMED");
        tempReservation.setId(null); 
        Reservation finalReservation = reservationRepository.save(tempReservation);

        // Sync reservation to Neo4j
        try {
            reservationGraphRepository.createReservation(
                finalReservation.getId(),             // The new Mongo ID
                user.getId(),                         // Matches the :User(userId) in Neo4j
                property.getId(),                     // Matches the :Property(propertyId) in Neo4j
                finalReservation.getDates().getCheckIn(),
                finalReservation.getDates().getCheckOut(),
                amountToPay
            );
            System.out.println("Graph: Reservation synced to Neo4j successfully.");
        } catch (Exception e) {
            // We log the error but don't stop the process, as the main data is safe in Mongo
            System.err.println("Graph Error: Failed to sync reservation to Neo4j. " + e.getMessage());
        }

        // Notify Manager of New Booking
        notificationService.notifyManagerOfNewBooking(
            property.getManagerId(), // Manager
            user.getId(),    // Customer
            finalReservation.getId()
        );

        // Cleanup Redis
        redisTemplate.delete(redisKey);

        return mapToDTO(finalReservation, room, "Payment successful! €" + String.format("%.2f", amountToPay) + " charged using card ending in " + method.getLast4Digits());
    }

    // Payment simulation with gateway token
    private boolean simulateBankChargeWithToken(String username, double amount, String token) {
        System.out.println("\n--- [SECURE BANK GATEWAY] ---");
        System.out.println("PROCESSING TRANSACTION FOR: " + username);
        System.out.println("USING GATEWAY TOKEN: " + token);
        System.out.println("AMOUNT TO CHARGE: €" + String.format("%.2f", amount));
        System.out.println("STATUS: TRANSACTION APPROVED");
        System.out.println("------------------------------\n");
        return true;
    }

    // MODIFY
    public ReservationResponseDTO modifyReservation(String token, String reservationId, ReservationRequestDTO newData) {
        // Check if CheckIn is equal or after CheckOut
        if (!newData.getCheckIn().isBefore(newData.getCheckOut())) {
            throw new IllegalArgumentException("Invalid dates: Check-out date must be strictly after Check-in date.");
        }
        
        // Check if CheckIn is in the past
        if (newData.getCheckIn().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Invalid dates: Check-in date cannot be in the past.");
        }
        
        String userId = jwtUtil.getUserIdFromToken(token);
        
        // Find the existing reservation
        Reservation existingRes = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        
        // Security and status checks
        if (!existingRes.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to modify this reservation.");
        }
        if ("CANCELLED".equalsIgnoreCase(existingRes.getStatus())) {
            throw new IllegalStateException("Cannot modify a cancelled reservation.");
        }

        // Get user nad payment method
        Customer user = (Customer) userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (user.getPaymentMethod() == null) {
            throw new IllegalStateException("No payment method found on file to process price changes.");
        }
        
        // Use the primary payment method for the transaction
        PaymentMethod method = user.getPaymentMethod();
        String gatewayToken = method.getGatewayToken();

        // Resolve Property and Rooms
        Property currentProp = propertyRepository.findByRoomsId(existingRes.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Property not found."));

        Room currentRoom = currentProp.getRooms().stream()
                .filter(r -> r.getId().equals(existingRes.getRoomId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Current room no longer exists."));

        // Find the target room
        Room targetRoom = currentProp.getRooms().stream()
                .filter(r -> r.getId().trim().equalsIgnoreCase(newData.getRoomId().trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Target room '" + newData.getRoomId() + "' not found."));

        // Check if target room is available (if changing rooms)
        if (!targetRoom.getId().equals(currentRoom.getId()) && !"available".equalsIgnoreCase(targetRoom.getStatus())) {
            throw new IllegalStateException("The selected new room is currently unavailable.");
        }

        // Validations (occupancy and conflicts)
        validateOccupancy(targetRoom, newData.getAdults(), newData.getChildren());

        List<Reservation> potentialConflicts = reservationRepository.findOverlappingReservations(
                targetRoom.getId(), newData.getCheckIn(), newData.getCheckOut());

        boolean actualConflict = potentialConflicts.stream()
                .anyMatch(r -> !r.getId().equals(reservationId) && !"CANCELLED".equalsIgnoreCase(r.getStatus()));

        if (actualConflict) {
            throw new IllegalStateException("The selected room/dates are already booked.");
        }

        // Payment logic with gateway token
        double oldPrice = calculateTotalPrice(currentRoom, existingRes.getDates().getCheckIn(), 
                                            existingRes.getDates().getCheckOut(), existingRes.getAdults(), existingRes.getChildren());
        
        double newPrice = calculateTotalPrice(targetRoom, newData.getCheckIn(), 
                                            newData.getCheckOut(), newData.getAdults(), newData.getChildren());

        double difference = newPrice - oldPrice;
        String financeMessage;

        if (difference > 0.01) {
            // Charge the additional amount using the token
            boolean success = simulateBankChargeWithToken(user.getUsername(), difference, gatewayToken);
            if (!success) throw new IllegalStateException("Additional payment declined.");
            financeMessage = "Modified. Additional charge: €" + String.format("%.2f", difference) + " (Card ending in " + method.getLast4Digits() + ")";
        } else if (difference < -0.01) {
            // Refund the difference using the token
            simulateBankRefundWithToken(user.getUsername(), Math.abs(difference), gatewayToken);
            financeMessage = "Modified. Refund processed: €" + String.format("%.2f", Math.abs(difference)) + " (Card ending in " + method.getLast4Digits() + ")";
        } else {
            financeMessage = "Modified. No price change required.";
        }

        // Update and save data
        existingRes.setRoomId(targetRoom.getId());
        existingRes.setDates(new Reservation.ReservationDates(newData.getCheckIn(), newData.getCheckOut()));
        existingRes.setAdults(newData.getAdults());
        existingRes.setChildren(newData.getChildren());
        
        Reservation updated = reservationRepository.save(existingRes);
        
        // Notify Manager of the modified booking
        notificationService.notifyManagerOfModification(
            currentProp.getManagerId(), // Manager
            user.getId(),    // Customer
            updated.getId()
        );

        return mapToDTO(updated, targetRoom, financeMessage);
    }

    // CANCEL
    public void cancelReservation(String token, String reservationId) {
        // Identify the user from the token
        String userId = jwtUtil.getUserIdFromToken(token);
        
        // Find the reservation and verify ownership
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (!reservation.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to cancel this reservation.");
        }

        if (!reservation.getStatus().equals("CONFIRMED")) {
            throw new IllegalStateException("Only confirmed reservations can be cancelled.");
        }

        // Cannot cancel if the check-in date has already passed
        if (reservation.getDates().getCheckIn().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Cannot cancel past or ongoing reservations.");
        }

        // Find user and token for refund
        Customer user = (Customer) userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        
        if (user.getPaymentMethod() == null) {
            throw new IllegalStateException("No payment method found to process the refund.");
        }
        
        // Retrieve the token and card details
        largebeb.utilities.PaymentMethod method = user.getPaymentMethod();
        String gatewayToken = method.getGatewayToken();

        // Calculate the total amount to be refunded
        Property property = propertyRepository.findByRoomsId(reservation.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Property not found for this reservation."));

        Room room = property.getRooms().stream()
                .filter(r -> r.getId().equals(reservation.getRoomId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Room details not found."));

        double amountPaid = calculateTotalPrice(
                room,
                reservation.getDates().getCheckIn(),
                reservation.getDates().getCheckOut(),
                reservation.getAdults(),
                reservation.getChildren()
        );

        // Process refund with gateway token
        // Using the secure tokenized helper method
        simulateBankRefundWithToken(user.getUsername(), amountPaid, gatewayToken);

        // Update status to CANCELLED and persist
        reservation.setStatus("CANCELLED");
        reservationRepository.save(reservation);
        
        // Delete reservation from Neo4j
        try {
            reservationGraphRepository.deleteById(reservationId);
            System.out.println("Graph: Reservation deleted/cancelled in Neo4j.");
        } catch (Exception e) {
            System.err.println("Graph Error: Failed to delete reservation from Neo4j.");
        }

        // Notify Manager of the cancelled booking
        notificationService.notifyManagerOfCancellation(
            property.getManagerId(), // Manager
            user.getId(),    // Customer
            reservation.getId()
        );

        System.out.println("Reservation " + reservationId + " cancelled and refund of €" + amountPaid + " issued to card: " + method.getLast4Digits());
    }

    // Refund simulation with gateway token
    private void simulateBankRefundWithToken(String username, double amount, String token) {
        System.out.println("\n--- [BANK GATEWAY - REFUND] ---");
        System.out.println("USER: " + username + " | TOKEN: " + token);
        System.out.println("AMOUNT: €" + String.format("%.2f", amount));
        System.out.println("STATUS: REFUNDED");
        System.out.println("-------------------------------\n");
    }
    // HELPER METHODS

    // Check capacity room
    private void validateOccupancy(Room room, int adults, int children) {
        // Validation for minimum number of people
        if (adults < 1) {
            throw new IllegalArgumentException("At least 1 adult is required per reservation.");
        }
        if (children < 0) {
            throw new IllegalArgumentException("Children count cannot be negative.");
        }
        // Validation against room capacity
        if (adults > room.getCapacityAdults()) {
            throw new IllegalArgumentException("Too many adults. Max allowed for this room: " + room.getCapacityAdults());
        }
        if (children > room.getCapacityChildren()) {
            throw new IllegalArgumentException("Too many children. Max allowed for this room: " + room.getCapacityChildren());
        }
    }

    private double calculateTotalPrice(Room room, LocalDate checkIn, LocalDate checkOut, int adults, int children) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < 1) nights = 1;

        double priceAdults = room.getPricePerNightAdults() != null ? room.getPricePerNightAdults() : 0.0;
        double priceChildren = room.getPricePerNightChildren() != null ? room.getPricePerNightChildren() : 0.0;

        double nightlyCost = (adults * priceAdults) + (children * priceChildren);
        return nightlyCost * nights;
    }

    private boolean isOverlapping(Reservation.ReservationDates existing, LocalDate newIn, LocalDate newOut) {
        return newIn.isBefore(existing.getCheckOut()) && newOut.isAfter(existing.getCheckIn());
    }    

    // Maps Reservation to ReservationResponseDTO using Room details for price, image, and message
    private ReservationResponseDTO mapToDTO(Reservation res, Room room, String message) {
        String roomName = (room != null) ? room.getName() : "Unknown Room";
        
        // Get first photo if available
        String mainImage = (room != null && room.getPhotos() != null && !room.getPhotos().isEmpty()) 
                           ? room.getPhotos().get(0) 
                           : null;

        double totalPrice = 0.0;
        if (room != null) {
            totalPrice = calculateTotalPrice(
                room, 
                res.getDates().getCheckIn(), 
                res.getDates().getCheckOut(), 
                res.getAdults(), 
                res.getChildren()
            );
        }

        return ReservationResponseDTO.builder()
                .id(res.getId())
                .roomName(roomName)
                .propertyId(propertyRepository.findByRoomsId(res.getRoomId())
                                .map(Property::getId)
                                .orElse(null))
                .roomId(res.getRoomId())
                .status(res.getStatus())
                .adults(res.getAdults())
                .children(res.getChildren())
                .checkIn(res.getDates().getCheckIn())
                .checkOut(res.getDates().getCheckOut())
                .totalPrice(totalPrice)
                .mainImage(mainImage)
                .message(message)
                .build();
    }
    
    // Fetch user reservations
    public List<ReservationResponseDTO> getUserReservations(String token) {
        String userId = jwtUtil.getUserIdFromToken(token);
        
        List<Reservation> reservations = reservationRepository.findByUserId(userId);

        return reservations.stream()
            .map(res -> {
                // Find the correct room in the property
                Property prop = propertyRepository.findByRoomsId(res.getRoomId()).orElse(null);
                Room room = (prop != null) ? prop.getRooms().stream()
                        .filter(r -> r.getId().equals(res.getRoomId()))
                        .findFirst().orElse(null) : null;
                
                return mapToDTO(res, room, null);
            })
            .collect(Collectors.toList());  
    }
}