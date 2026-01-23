package largebeb.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AnalyticsResponseDTO {
    
    // Property Info
    private String propertyId;
    private String propertyName;
    
    // Date Range
    private LocalDate periodStart;
    private LocalDate periodEnd;
    
    // Reservation Statistics
    private long totalReservations;
    private long confirmedReservations;
    private long cancelledReservations;
    private long completedReservations;
    
    // Revenue
    private double totalRevenue;
    private double averageRevenuePerReservation;
    
    // Occupancy
    private double occupancyRate; // Percentage
    private long totalNightsBooked;
    
    // Guest Statistics
    private long totalGuests;
    private long totalAdults;
    private long totalChildren;
    
    // Room Performance
    private List<RoomAnalyticsDTO> roomAnalytics;
    
    // Monthly Breakdown (if requested)
    private Map<String, MonthlyStatsDTO> monthlyBreakdown;
    
    @Data
    @Builder
    public static class RoomAnalyticsDTO {
        private String roomId;
        private String roomName;
        private long reservationCount;
        private double revenue;
        private double occupancyRate;
    }
    
    @Data
    @Builder
    public static class MonthlyStatsDTO {
        private long reservations;
        private double revenue;
        private long guests;
    }
}
