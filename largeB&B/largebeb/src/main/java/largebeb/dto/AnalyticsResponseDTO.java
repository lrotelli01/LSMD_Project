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
    
    // Additional Required Metrics
    private String mostBookedRoomType;        // Most frequently booked room type
    private double avgGuestsPerRoomPerBooking; // Average guests per room per booking
    
    // Room Performance
    private List<RoomAnalyticsDTO> roomAnalytics;
    
    // Monthly Breakdown (if requested)
    private Map<String, MonthlyStatsDTO> monthlyBreakdown;
    
    // Rating Evolution
    private RatingAnalyticsDTO ratingAnalytics;
    
    // Reservation Trends
    private ReservationTrendsDTO reservationTrends;
    
    // Comparative Performance
    private ComparativePerformanceDTO comparativePerformance;
    
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
    
    // DTOs for Advanced Analytics
    
    /**
     * Rating Evolution - tracks how ratings change over time
     */
    @Data
    @Builder
    public static class RatingAnalyticsDTO {
        private double currentAverageRating;
        private double previousPeriodRating; // For comparison
        private double ratingTrend; // Positive = improving, Negative = declining
        private long totalReviews;
        private Map<String, Double> monthlyAverageRatings; // "2025-01" -> 4.5
        
        // Breakdown by category
        private double avgCleanliness;
        private double avgCommunication;
        private double avgLocation;
        private double avgValue;
        
        // Rating distribution (how many 1-star, 2-star, etc.)
        private Map<Integer, Long> ratingDistribution; // 1 -> 5, 2 -> 12, 3 -> 25, etc.
        
        // Best/Worst performing aspects
        private String bestAspect;
        private String worstAspect;
    }
    
    /**
     * Reservation Trends - booking patterns analysis
     */
    @Data
    @Builder
    public static class ReservationTrendsDTO {
        // Booking velocity
        private double avgBookingsPerMonth;
        private double bookingGrowthRate; // % change vs previous period
        
        // Time-based patterns
        private Map<String, Long> bookingsByDayOfWeek; // "MONDAY" -> 15, "TUESDAY" -> 22
        private Map<String, Long> bookingsByMonth; // "2025-01" -> 8, "2025-02" -> 12
        
        // Lead time analysis (days between booking and check-in)
        private double avgLeadTimeDays;
        private double minLeadTimeDays;
        private double maxLeadTimeDays;
        
        // Stay duration patterns
        private double avgStayDuration;
        private Map<String, Long> stayDurationDistribution; // "1-2 nights" -> 20, "3-5 nights" -> 35
        
        // Cancellation analysis
        private double cancellationRate;
        private Map<String, Double> monthlyCancellationRates;
        
        // Peak/Off-peak identification
        private List<String> peakMonths;
        private List<String> lowSeasonMonths;
    }
    
    /**
     * Comparative Performance - benchmarking against similar properties
     */
    @Data
    @Builder
    public static class ComparativePerformanceDTO {
        // How this property compares to others in same area
        private String comparisonScope; // e.g., "Same City", "Same Region"
        private int propertiesCompared;
        
        // Revenue comparison
        private double propertyRevenue;
        private double avgMarketRevenue;
        private double revenuePercentile; // e.g., 75 means better than 75% of competitors
        
        // Occupancy comparison
        private double propertyOccupancy;
        private double avgMarketOccupancy;
        private double occupancyPercentile;
        
        // Rating comparison
        private double propertyRating;
        private double avgMarketRating;
        private double ratingPercentile;
        
        // Price comparison
        private double propertyAvgPrice;
        private double marketAvgPrice;
        private String pricePositioning; // "Budget", "Mid-Range", "Premium"
        
        // Performance score (composite metric)
        private double overallPerformanceScore; // 0-100
        private String performanceCategory; // "Top Performer", "Average", "Needs Improvement"
    }
}
