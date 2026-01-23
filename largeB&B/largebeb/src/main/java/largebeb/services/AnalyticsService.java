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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PropertyRepository propertyRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /**
     * Get analytics for a specific property with optional date range
     * If no dates provided, shows all-time analytics (from property creation)
     */
    public AnalyticsResponseDTO getPropertyAnalytics(String token, String propertyId, 
                                                      LocalDate startDate, LocalDate endDate) {
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only view analytics of your own properties.");
        }

        return calculateAnalytics(property, startDate, endDate);
    }

    /**
     * Get analytics summary for all properties owned by the manager
     */
    public List<AnalyticsResponseDTO> getAllPropertiesAnalytics(String token, 
                                                                 LocalDate startDate, LocalDate endDate) {
        RegisteredUser manager = getManagerFromToken(token);
        
        List<Property> myProperties = propertyRepository.findByManagerId(manager.getId());

        return myProperties.stream()
                .map(p -> calculateAnalytics(p, startDate, endDate))
                .collect(Collectors.toList());
    }

    /**
     * Get aggregated analytics across all properties
     */
    public AnalyticsResponseDTO getAggregatedAnalytics(String token, LocalDate startDate, LocalDate endDate) {
        RegisteredUser manager = getManagerFromToken(token);
        
        List<Property> myProperties = propertyRepository.findByManagerId(manager.getId());

        if (myProperties.isEmpty()) {
            return AnalyticsResponseDTO.builder()
                    .propertyName("All Properties")
                    .periodStart(startDate)
                    .periodEnd(endDate)
                    .totalReservations(0)
                    .totalRevenue(0.0)
                    .build();
        }

        // Collect all room IDs from all properties
        List<String> allRoomIds = myProperties.stream()
                .filter(p -> p.getRooms() != null)
                .flatMap(p -> p.getRooms().stream())
                .map(Room::getId)
                .collect(Collectors.toList());

        // Get all reservations for these rooms
        List<Reservation> allReservations = reservationRepository.findByRoomIdIn(allRoomIds);

        // Filter by date if provided
        if (startDate != null && endDate != null) {
            allReservations = allReservations.stream()
                    .filter(r -> !r.getDates().getCheckIn().isBefore(startDate) && 
                                 !r.getDates().getCheckOut().isAfter(endDate.plusDays(1)))
                    .collect(Collectors.toList());
        }

        // Calculate aggregated stats
        long totalRes = allReservations.size();
        long confirmed = allReservations.stream().filter(r -> "CONFIRMED".equalsIgnoreCase(r.getStatus())).count();
        long cancelled = allReservations.stream().filter(r -> "CANCELLED".equalsIgnoreCase(r.getStatus())).count();
        long completed = allReservations.stream().filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus())).count();

        double totalRevenue = calculateTotalRevenue(allReservations, myProperties);

        long totalGuests = allReservations.stream()
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                .mapToLong(r -> r.getAdults() + r.getChildren())
                .sum();

        return AnalyticsResponseDTO.builder()
                .propertyName("All Properties (Aggregated)")
                .periodStart(startDate)
                .periodEnd(endDate)
                .totalReservations(totalRes)
                .confirmedReservations(confirmed)
                .cancelledReservations(cancelled)
                .completedReservations(completed)
                .totalRevenue(totalRevenue)
                .averageRevenuePerReservation(totalRes > 0 ? totalRevenue / (totalRes - cancelled) : 0)
                .totalGuests(totalGuests)
                .build();
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private AnalyticsResponseDTO calculateAnalytics(Property property, LocalDate startDate, LocalDate endDate) {
        // Get all room IDs for this property
        List<String> roomIds = property.getRooms() != null 
                ? property.getRooms().stream().map(Room::getId).collect(Collectors.toList())
                : new ArrayList<>();

        // Get all reservations for these rooms
        List<Reservation> reservations = roomIds.isEmpty() 
                ? new ArrayList<>() 
                : reservationRepository.findByRoomIdIn(roomIds);

        // Filter by date range if provided
        LocalDate effectiveStart = startDate;
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        if (startDate != null) {
            reservations = reservations.stream()
                    .filter(r -> !r.getDates().getCheckIn().isBefore(startDate) && 
                                 !r.getDates().getCheckOut().isAfter(effectiveEnd.plusDays(1)))
                    .collect(Collectors.toList());
        }

        // Calculate statistics
        long totalRes = reservations.size();
        long confirmed = reservations.stream().filter(r -> "CONFIRMED".equalsIgnoreCase(r.getStatus())).count();
        long cancelled = reservations.stream().filter(r -> "CANCELLED".equalsIgnoreCase(r.getStatus())).count();
        long completed = reservations.stream().filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus())).count();

        // Revenue calculation (only for non-cancelled reservations)
        double totalRevenue = 0.0;
        long totalNights = 0;
        long totalAdults = 0;
        long totalChildren = 0;

        for (Reservation res : reservations) {
            if ("CANCELLED".equalsIgnoreCase(res.getStatus())) continue;

            Room room = findRoomById(property, res.getRoomId());
            if (room != null) {
                long nights = ChronoUnit.DAYS.between(res.getDates().getCheckIn(), res.getDates().getCheckOut());
                if (nights < 1) nights = 1;
                totalNights += nights;

                double priceAdults = room.getPricePerNightAdults() != null ? room.getPricePerNightAdults() : 0.0;
                double priceChildren = room.getPricePerNightChildren() != null ? room.getPricePerNightChildren() : 0.0;
                totalRevenue += (res.getAdults() * priceAdults + res.getChildren() * priceChildren) * nights;
            }

            totalAdults += res.getAdults();
            totalChildren += res.getChildren();
        }

        // Calculate occupancy rate
        double occupancyRate = calculateOccupancyRate(property, reservations, effectiveStart, effectiveEnd);

        // Per-room analytics
        List<AnalyticsResponseDTO.RoomAnalyticsDTO> roomAnalytics = calculateRoomAnalytics(property, reservations);

        // Monthly breakdown
        Map<String, AnalyticsResponseDTO.MonthlyStatsDTO> monthlyBreakdown = calculateMonthlyBreakdown(reservations, property);

        return AnalyticsResponseDTO.builder()
                .propertyId(property.getId())
                .propertyName(property.getName())
                .periodStart(effectiveStart)
                .periodEnd(effectiveEnd)
                .totalReservations(totalRes)
                .confirmedReservations(confirmed)
                .cancelledReservations(cancelled)
                .completedReservations(completed)
                .totalRevenue(Math.round(totalRevenue * 100.0) / 100.0)
                .averageRevenuePerReservation(totalRes - cancelled > 0 ? Math.round((totalRevenue / (totalRes - cancelled)) * 100.0) / 100.0 : 0)
                .occupancyRate(Math.round(occupancyRate * 100.0) / 100.0)
                .totalNightsBooked(totalNights)
                .totalGuests(totalAdults + totalChildren)
                .totalAdults(totalAdults)
                .totalChildren(totalChildren)
                .roomAnalytics(roomAnalytics)
                .monthlyBreakdown(monthlyBreakdown)
                .build();
    }

    private double calculateOccupancyRate(Property property, List<Reservation> reservations, 
                                          LocalDate startDate, LocalDate endDate) {
        if (property.getRooms() == null || property.getRooms().isEmpty()) return 0.0;
        if (startDate == null || endDate == null) return 0.0;

        int numRooms = property.getRooms().size();
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (totalDays <= 0) totalDays = 1;

        long totalAvailableNights = numRooms * totalDays;

        long bookedNights = reservations.stream()
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                .mapToLong(r -> ChronoUnit.DAYS.between(
                        r.getDates().getCheckIn().isBefore(startDate) ? startDate : r.getDates().getCheckIn(),
                        r.getDates().getCheckOut().isAfter(endDate) ? endDate : r.getDates().getCheckOut()))
                .sum();

        return totalAvailableNights > 0 ? (bookedNights * 100.0 / totalAvailableNights) : 0.0;
    }

    private List<AnalyticsResponseDTO.RoomAnalyticsDTO> calculateRoomAnalytics(Property property, 
                                                                                List<Reservation> reservations) {
        if (property.getRooms() == null) return new ArrayList<>();

        return property.getRooms().stream().map(room -> {
            List<Reservation> roomReservations = reservations.stream()
                    .filter(r -> room.getId().equals(r.getRoomId()) && !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                    .collect(Collectors.toList());

            double revenue = 0.0;
            for (Reservation res : roomReservations) {
                long nights = ChronoUnit.DAYS.between(res.getDates().getCheckIn(), res.getDates().getCheckOut());
                if (nights < 1) nights = 1;
                double priceAdults = room.getPricePerNightAdults() != null ? room.getPricePerNightAdults() : 0.0;
                double priceChildren = room.getPricePerNightChildren() != null ? room.getPricePerNightChildren() : 0.0;
                revenue += (res.getAdults() * priceAdults + res.getChildren() * priceChildren) * nights;
            }

            return AnalyticsResponseDTO.RoomAnalyticsDTO.builder()
                    .roomId(room.getId())
                    .roomName(room.getName())
                    .reservationCount(roomReservations.size())
                    .revenue(Math.round(revenue * 100.0) / 100.0)
                    .build();
        }).collect(Collectors.toList());
    }

    private Map<String, AnalyticsResponseDTO.MonthlyStatsDTO> calculateMonthlyBreakdown(
            List<Reservation> reservations, Property property) {
        
        Map<String, AnalyticsResponseDTO.MonthlyStatsDTO> breakdown = new TreeMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (Reservation res : reservations) {
            if ("CANCELLED".equalsIgnoreCase(res.getStatus())) continue;

            String month = res.getDates().getCheckIn().format(formatter);
            
            Room room = findRoomById(property, res.getRoomId());
            final double resRevenue;
            if (room != null) {
                long nights = ChronoUnit.DAYS.between(res.getDates().getCheckIn(), res.getDates().getCheckOut());
                if (nights < 1) nights = 1;
                double priceAdults = room.getPricePerNightAdults() != null ? room.getPricePerNightAdults() : 0.0;
                double priceChildren = room.getPricePerNightChildren() != null ? room.getPricePerNightChildren() : 0.0;
                resRevenue = (res.getAdults() * priceAdults + res.getChildren() * priceChildren) * nights;
            } else {
                resRevenue = 0.0;
            }

            long guests = res.getAdults() + res.getChildren();

            breakdown.merge(month, 
                    AnalyticsResponseDTO.MonthlyStatsDTO.builder()
                            .reservations(1)
                            .revenue(resRevenue)
                            .guests(guests)
                            .build(),
                    (existing, newVal) -> AnalyticsResponseDTO.MonthlyStatsDTO.builder()
                            .reservations(existing.getReservations() + 1)
                            .revenue(existing.getRevenue() + resRevenue)
                            .guests(existing.getGuests() + guests)
                            .build());
        }

        return breakdown;
    }

    private double calculateTotalRevenue(List<Reservation> reservations, List<Property> properties) {
        double total = 0.0;
        
        for (Reservation res : reservations) {
            if ("CANCELLED".equalsIgnoreCase(res.getStatus())) continue;

            // Find the room
            for (Property prop : properties) {
                if (prop.getRooms() == null) continue;
                
                Room room = prop.getRooms().stream()
                        .filter(r -> r.getId().equals(res.getRoomId()))
                        .findFirst()
                        .orElse(null);
                
                if (room != null) {
                    long nights = ChronoUnit.DAYS.between(res.getDates().getCheckIn(), res.getDates().getCheckOut());
                    if (nights < 1) nights = 1;
                    double priceAdults = room.getPricePerNightAdults() != null ? room.getPricePerNightAdults() : 0.0;
                    double priceChildren = room.getPricePerNightChildren() != null ? room.getPricePerNightChildren() : 0.0;
                    total += (res.getAdults() * priceAdults + res.getChildren() * priceChildren) * nights;
                    break;
                }
            }
        }

        return Math.round(total * 100.0) / 100.0;
    }

    private Room findRoomById(Property property, String roomId) {
        if (property.getRooms() == null) return null;
        return property.getRooms().stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .orElse(null);
    }

    private RegisteredUser getManagerFromToken(String token) {
        String cleanToken = token.replace("Bearer ", "");
        String userId = jwtUtil.getUserIdFromToken(cleanToken);
        
        RegisteredUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"MANAGER".equalsIgnoreCase(user.getRole())) {
            throw new SecurityException("Access Denied: Only managers can view analytics.");
        }

        return user;
    }
}
