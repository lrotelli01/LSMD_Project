package largebeb.services;

import largebeb.dto.*;
import largebeb.model.Property;
import largebeb.model.RegisteredUser;
import largebeb.model.Reservation;
import largebeb.model.Room;
import largebeb.repository.PropertyRepository;
import largebeb.repository.ReservationRepository;
import largebeb.repository.UserRepository;
import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerReservationService {

    private final PropertyRepository propertyRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /**
     * Get all reservations for all properties owned by the manager
     * Supports optional date filtering
     */
    public List<ManagerReservationDTO> getAllMyReservations(String token, LocalDate startDate, LocalDate endDate) {
        RegisteredUser manager = getManagerFromToken(token);
        
        // Get all properties owned by this manager
        List<Property> myProperties = propertyRepository.findByManagerId(manager.getId());

        if (myProperties.isEmpty()) {
            return new ArrayList<>();
        }

        // Collect all room IDs from all properties
        List<String> allRoomIds = myProperties.stream()
                .filter(p -> p.getRooms() != null)
                .flatMap(p -> p.getRooms().stream())
                .map(Room::getId)
                .collect(Collectors.toList());

        if (allRoomIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Get all reservations for these rooms
        List<Reservation> allReservations = reservationRepository.findByRoomIdIn(allRoomIds);

        // Apply date filtering if provided
        return allReservations.stream()
                .filter(res -> filterByDateRange(res, startDate, endDate))
                .map(res -> mapToManagerReservationDTO(res, myProperties))
                .sorted(Comparator.comparing(ManagerReservationDTO::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get reservations for a specific property
     * Supports optional date filtering
     */
    public List<ManagerReservationDTO> getPropertyReservations(String token, String propertyId, 
                                                                LocalDate startDate, LocalDate endDate) {
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only view reservations of your own properties.");
        }

        if (property.getRooms() == null || property.getRooms().isEmpty()) {
            return new ArrayList<>();
        }

        // Get room IDs for this property
        List<String> roomIds = property.getRooms().stream()
                .map(Room::getId)
                .collect(Collectors.toList());

        // Get reservations
        List<Reservation> reservations = reservationRepository.findByRoomIdIn(roomIds);

        return reservations.stream()
                .filter(res -> filterByDateRange(res, startDate, endDate))
                .map(res -> mapToManagerReservationDTO(res, List.of(property)))
                .sorted(Comparator.comparing(ManagerReservationDTO::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Filter reservation by date range
     * @param res The reservation to check
     * @param startDate If provided, reservation checkIn must be >= startDate
     * @param endDate If provided, reservation checkOut must be <= endDate
     */
    private boolean filterByDateRange(Reservation res, LocalDate startDate, LocalDate endDate) {
        if (res.getDates() == null) {
            return false;
        }
        if (startDate != null && res.getDates().getCheckIn() != null 
                && res.getDates().getCheckIn().isBefore(startDate)) {
            return false;
        }
        if (endDate != null && res.getDates().getCheckOut() != null 
                && res.getDates().getCheckOut().isAfter(endDate)) {
            return false;
        }
        return true;
    }

    /**
     * Get reservations filtered by status (uses overloaded method with null dates)
     */
    public List<ManagerReservationDTO> getReservationsByStatus(String token, String status) {
        List<ManagerReservationDTO> allReservations = getAllMyReservations(token, null, null);
        
        return allReservations.stream()
                .filter(r -> status.equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Get upcoming reservations (check-in date in the future)
     */
    public List<ManagerReservationDTO> getUpcomingReservations(String token) {
        List<ManagerReservationDTO> allReservations = getAllMyReservations(token, null, null);
        LocalDate today = LocalDate.now();
        
        return allReservations.stream()
                .filter(r -> r.getCheckIn().isAfter(today) && "CONFIRMED".equalsIgnoreCase(r.getStatus()))
                .sorted(Comparator.comparing(ManagerReservationDTO::getCheckIn))
                .collect(Collectors.toList());
    }

    /**
     * Get current reservations (ongoing stays)
     */
    public List<ManagerReservationDTO> getCurrentReservations(String token) {
        List<ManagerReservationDTO> allReservations = getAllMyReservations(token, null, null);
        LocalDate today = LocalDate.now();
        
        return allReservations.stream()
                .filter(r -> !r.getCheckIn().isAfter(today) && !r.getCheckOut().isBefore(today) 
                             && "CONFIRMED".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Get payment status for all rooms in manager's properties
     */
    public List<RoomPaymentStatusDTO> getPaymentStatus(String token) {
        RegisteredUser manager = getManagerFromToken(token);
        
        List<Property> myProperties = propertyRepository.findByManagerId(manager.getId());

        return myProperties.stream()
                .map(this::buildPropertyPaymentStatus)
                .collect(Collectors.toList());
    }

    /**
     * Get payment status for a specific property
     */
    public RoomPaymentStatusDTO getPropertyPaymentStatus(String token, String propertyId) {
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only view payment status of your own properties.");
        }

        return buildPropertyPaymentStatus(property);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private RoomPaymentStatusDTO buildPropertyPaymentStatus(Property property) {
        List<RoomPaymentStatusDTO.RoomStatusDetail> roomDetails = new ArrayList<>();
        LocalDate today = LocalDate.now();

        if (property.getRooms() != null) {
            for (Room room : property.getRooms()) {
                // Get all reservations for this room
                List<Reservation> roomReservations = reservationRepository.findByRoomId(room.getId());

                // Find current reservation (if any)
                Reservation currentRes = roomReservations.stream()
                        .filter(r -> !r.getDates().getCheckIn().isAfter(today) 
                                     && !r.getDates().getCheckOut().isBefore(today)
                                     && !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                        .findFirst()
                        .orElse(null);

                // Count upcoming reservations
                long upcomingCount = roomReservations.stream()
                        .filter(r -> r.getDates().getCheckIn().isAfter(today) 
                                     && "CONFIRMED".equalsIgnoreCase(r.getStatus()))
                        .count();

                // Calculate total revenue from completed bookings
                double totalRevenue = roomReservations.stream()
                        .filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus()) || "CONFIRMED".equalsIgnoreCase(r.getStatus()))
                        .mapToDouble(r -> calculateReservationPrice(r, room))
                        .sum();

                long completedCount = roomReservations.stream()
                        .filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus()))
                        .count();

                RoomPaymentStatusDTO.RoomStatusDetail detail = RoomPaymentStatusDTO.RoomStatusDetail.builder()
                        .roomId(room.getId())
                        .roomName(room.getName())
                        .roomType(room.getRoomType())
                        .availabilityStatus(room.getStatus())
                        .currentlyOccupied(currentRes != null)
                        .currentReservationId(currentRes != null ? currentRes.getId() : null)
                        .currentGuestId(currentRes != null ? currentRes.getUserId() : null)
                        .currentCheckIn(currentRes != null ? currentRes.getDates().getCheckIn() : null)
                        .currentCheckOut(currentRes != null ? currentRes.getDates().getCheckOut() : null)
                        .paymentStatus(currentRes != null ? currentRes.getStatus() : "NO_BOOKING")
                        .upcomingReservations(upcomingCount)
                        .totalRevenueGenerated(Math.round(totalRevenue * 100.0) / 100.0)
                        .totalCompletedBookings(completedCount)
                        .build();

                roomDetails.add(detail);
            }
        }

        return RoomPaymentStatusDTO.builder()
                .propertyId(property.getId())
                .propertyName(property.getName())
                .rooms(roomDetails)
                .build();
    }

    private double calculateReservationPrice(Reservation res, Room room) {
        long nights = ChronoUnit.DAYS.between(res.getDates().getCheckIn(), res.getDates().getCheckOut());
        if (nights < 1) nights = 1;
        
        double priceAdults = room.getPricePerNightAdults() != null ? room.getPricePerNightAdults() : 0.0;
        double priceChildren = room.getPricePerNightChildren() != null ? room.getPricePerNightChildren() : 0.0;
        
        return (res.getAdults() * priceAdults + res.getChildren() * priceChildren) * nights;
    }

    private ManagerReservationDTO mapToManagerReservationDTO(Reservation res, List<Property> properties) {
        // Find the property and room
        Property property = null;
        Room room = null;
        
        for (Property p : properties) {
            if (p.getRooms() == null) continue;
            
            room = p.getRooms().stream()
                    .filter(r -> r.getId().equals(res.getRoomId()))
                    .findFirst()
                    .orElse(null);
            
            if (room != null) {
                property = p;
                break;
            }
        }

        // Get guest info
        String guestName = "Unknown";
        String guestEmail = "";
        RegisteredUser guest = userRepository.findById(res.getUserId()).orElse(null);
        if (guest != null) {
            guestName = guest.getName() != null && guest.getSurname() != null 
                    ? guest.getName() + " " + guest.getSurname() 
                    : guest.getUsername();
            guestEmail = guest.getEmail();
        }

        // Calculate price
        double totalPrice = 0.0;
        if (room != null) {
            totalPrice = calculateReservationPrice(res, room);
        }

        return ManagerReservationDTO.builder()
                .reservationId(res.getId())
                .status(res.getStatus())
                .checkIn(res.getDates().getCheckIn())
                .checkOut(res.getDates().getCheckOut())
                .createdAt(res.getCreatedAt())
                .adults(res.getAdults())
                .children(res.getChildren())
                .guestId(res.getUserId())
                .guestName(guestName)
                .guestEmail(guestEmail)
                .propertyId(property != null ? property.getId() : null)
                .propertyName(property != null ? property.getName() : "Unknown Property")
                .roomId(res.getRoomId())
                .roomName(room != null ? room.getName() : "Unknown Room")
                .roomType(room != null ? room.getRoomType() : null)
                .totalPrice(Math.round(totalPrice * 100.0) / 100.0)
                .build();
    }

    private RegisteredUser getManagerFromToken(String token) {
        String cleanToken = token.replace("Bearer ", "");
        String userId = jwtUtil.getUserIdFromToken(cleanToken);
        
        RegisteredUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"MANAGER".equalsIgnoreCase(user.getRole())) {
            throw new SecurityException("Access Denied: Only managers can perform this action.");
        }

        return user;
    }
}
