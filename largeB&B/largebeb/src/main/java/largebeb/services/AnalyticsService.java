package largebeb.services;

import largebeb.dto.*;
import largebeb.dto.AnalyticsResponseDTO.*;
import largebeb.model.Property;
import largebeb.model.RegisteredUser;
import largebeb.model.Reservation;
import largebeb.model.Review;
import largebeb.model.Room;
import largebeb.repository.PropertyRepository;
import largebeb.repository.ReservationRepository;
import largebeb.repository.ReviewRepository;
import largebeb.repository.UserRepository;
import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AnalyticsService - Comprehensive Business Intelligence for Property Managers
 * 
 * Provides detailed analytics including:
 * - Revenue calculations with aggregation pipelines
 * - Occupancy rate analysis
 * - Reservation trends and booking patterns
 * - Rating evolution tracking
 * - Comparative performance benchmarking
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final PropertyRepository propertyRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final JwtUtil jwtUtil;

    /**
     * Get comprehensive analytics for a specific property with optional date range
     */
    public AnalyticsResponseDTO getPropertyAnalytics(String token, String propertyId, 
                                                      LocalDate startDate, LocalDate endDate) {
        log.info("Getting analytics for property: {} with date range: {} to {}", propertyId, startDate, endDate);
        
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> {
                    log.error("Property not found: {}", propertyId);
                    return new IllegalArgumentException("Property not found with id: " + propertyId);
                });

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            log.warn("Manager {} attempted to access analytics for property {} owned by another manager", 
                    manager.getId(), propertyId);
            throw new SecurityException("You can only view analytics of your own properties.");
        }

        return calculateComprehensiveAnalytics(property, startDate, endDate, manager.getId());
    }

    /**
     * Get analytics summary for all properties owned by the manager
     */
    public List<AnalyticsResponseDTO> getAllPropertiesAnalytics(String token, 
                                                                 LocalDate startDate, LocalDate endDate) {
        log.info("Getting analytics for all properties with date range: {} to {}", startDate, endDate);
        
        RegisteredUser manager = getManagerFromToken(token);
        List<Property> myProperties = propertyRepository.findByManagerId(manager.getId());

        log.info("Found {} properties for manager {}", myProperties.size(), manager.getId());

        return myProperties.stream()
                .map(p -> calculateComprehensiveAnalytics(p, startDate, endDate, manager.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Get aggregated analytics across all properties
     */
    public AnalyticsResponseDTO getAggregatedAnalytics(String token, LocalDate startDate, LocalDate endDate) {
        log.info("Getting aggregated analytics for all properties");
        
        RegisteredUser manager = getManagerFromToken(token);
        List<Property> myProperties = propertyRepository.findByManagerId(manager.getId());

        if (myProperties.isEmpty()) {
            log.info("No properties found for manager {}", manager.getId());
            return AnalyticsResponseDTO.builder()
                    .propertyName("All Properties")
                    .periodStart(startDate)
                    .periodEnd(endDate)
                    .totalReservations(0)
                    .totalRevenue(0.0)
                    .build();
        }

        // Use MongoDB Aggregation Pipeline for efficient computation
        return calculateAggregatedAnalyticsWithPipeline(myProperties, startDate, endDate, manager.getId());
    }

    /**
     * Get rating evolution analysis for a property
     */
    public RatingAnalyticsDTO getRatingEvolution(String token, String propertyId, 
                                                  LocalDate startDate, LocalDate endDate) {
        log.info("Getting rating evolution for property: {}", propertyId);
        
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only view analytics of your own properties.");
        }

        return calculateRatingAnalytics(propertyId, startDate, endDate);
    }

    /**
     * Get reservation trends analysis for a property
     */
    public ReservationTrendsDTO getReservationTrends(String token, String propertyId, 
                                                      LocalDate startDate, LocalDate endDate) {
        log.info("Getting reservation trends for property: {}", propertyId);
        
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only view analytics of your own properties.");
        }

        List<String> roomIds = getRoomIds(property);
        List<Reservation> reservations = roomIds.isEmpty() 
                ? new ArrayList<>() 
                : reservationRepository.findByRoomIdIn(roomIds);

        return calculateReservationTrends(reservations, startDate, endDate);
    }

    /**
     * Get comparative performance benchmarking
     */
    public ComparativePerformanceDTO getComparativePerformance(String token, String propertyId) {
        log.info("Getting comparative performance for property: {}", propertyId);
        
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only view analytics of your own properties.");
        }

        return calculateComparativePerformance(property);
    }

    // CORE ANALYTICS CALCULATION

    private AnalyticsResponseDTO calculateComprehensiveAnalytics(Property property, 
                                                                  LocalDate startDate, LocalDate endDate,
                                                                  String managerId) {
        log.debug("Calculating comprehensive analytics for property: {}", property.getName());

        List<String> roomIds = getRoomIds(property);
        List<Reservation> reservations = roomIds.isEmpty() 
                ? new ArrayList<>() 
                : reservationRepository.findByRoomIdIn(roomIds);

        LocalDate effectiveStart = startDate;
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        // Filter by date range
        if (startDate != null) {
            reservations = filterReservationsByDateRange(reservations, startDate, effectiveEnd);
        }

        // Basic statistics
        BasicStats stats = calculateBasicStats(reservations, property);

        // Calculate occupancy
        double occupancyRate = calculateOccupancyRate(property, reservations, effectiveStart, effectiveEnd);

        // Per-room analytics
        List<RoomAnalyticsDTO> roomAnalytics = calculateRoomAnalytics(property, reservations, effectiveStart, effectiveEnd);

        // Monthly breakdown
        Map<String, MonthlyStatsDTO> monthlyBreakdown = calculateMonthlyBreakdown(reservations, property);

        // Rating Analytics
        RatingAnalyticsDTO ratingAnalytics = calculateRatingAnalytics(property.getId(), effectiveStart, effectiveEnd);

        // Reservation Trends
        ReservationTrendsDTO reservationTrends = calculateReservationTrends(reservations, effectiveStart, effectiveEnd);

        // Comparative Performance
        ComparativePerformanceDTO comparativePerformance = calculateComparativePerformance(property);

        // Most booked room type
        String mostBookedRoomType = calculateMostBookedRoomType(reservations, property);

        // Average guests per room per booking
        double avgGuestsPerRoomPerBooking = calculateAvgGuestsPerRoomPerBooking(reservations);

        return AnalyticsResponseDTO.builder()
                .propertyId(property.getId())
                .propertyName(property.getName())
                .periodStart(effectiveStart)
                .periodEnd(effectiveEnd)
                .totalReservations(stats.totalReservations)
                .confirmedReservations(stats.confirmedReservations)
                .cancelledReservations(stats.cancelledReservations)
                .completedReservations(stats.completedReservations)
                .totalRevenue(roundToTwoDecimals(stats.totalRevenue))
                .averageRevenuePerReservation(stats.avgRevenuePerReservation)
                .occupancyRate(roundToTwoDecimals(occupancyRate))
                .totalNightsBooked(stats.totalNights)
                .totalGuests(stats.totalAdults + stats.totalChildren)
                .totalAdults(stats.totalAdults)
                .totalChildren(stats.totalChildren)
                .mostBookedRoomType(mostBookedRoomType)
                .avgGuestsPerRoomPerBooking(roundToTwoDecimals(avgGuestsPerRoomPerBooking))
                .roomAnalytics(roomAnalytics)
                .monthlyBreakdown(monthlyBreakdown)
                .ratingAnalytics(ratingAnalytics)
                .reservationTrends(reservationTrends)
                .comparativePerformance(comparativePerformance)
                .build();
    }

    // RATING EVOLUTION ANALYSIS

    /**
     * Calculates comprehensive rating analytics with evolution tracking
     */
    private RatingAnalyticsDTO calculateRatingAnalytics(String propertyId, LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating rating analytics for property: {}", propertyId);

        List<Review> allReviews = reviewRepository.findByPropertyIdOrderByCreationDateAsc(propertyId);
        
        if (allReviews.isEmpty()) {
            return RatingAnalyticsDTO.builder()
                    .currentAverageRating(0.0)
                    .totalReviews(0)
                    .ratingDistribution(new HashMap<>())
                    .monthlyAverageRatings(new HashMap<>())
                    .build();
        }

        // Filter by date range if provided
        List<Review> periodReviews = allReviews;
        if (startDate != null && endDate != null) {
            periodReviews = allReviews.stream()
                    .filter(r -> r.getCreationDate() != null)
                    .filter(r -> !r.getCreationDate().isBefore(startDate) && !r.getCreationDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Current average rating
        double currentAvg = periodReviews.stream()
                .filter(r -> r.getRating() != null)
                .mapToDouble(Review::getRating)
                .average()
                .orElse(0.0);

        // Previous period comparison (same duration before startDate)
        double previousPeriodRating = 0.0;
        double ratingTrend = 0.0;
        if (startDate != null && endDate != null) {
            long periodDays = ChronoUnit.DAYS.between(startDate, endDate);
            LocalDate prevStart = startDate.minusDays(periodDays);
            LocalDate prevEnd = startDate.minusDays(1);
            
            double prevAvg = allReviews.stream()
                    .filter(r -> r.getCreationDate() != null && r.getRating() != null)
                    .filter(r -> !r.getCreationDate().isBefore(prevStart) && !r.getCreationDate().isAfter(prevEnd))
                    .mapToDouble(Review::getRating)
                    .average()
                    .orElse(0.0);
            
            previousPeriodRating = prevAvg;
            ratingTrend = prevAvg > 0 ? ((currentAvg - prevAvg) / prevAvg) * 100 : 0;
        }

        // Monthly average ratings
        Map<String, Double> monthlyRatings = new TreeMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, List<Review>> byMonth = periodReviews.stream()
                .filter(r -> r.getCreationDate() != null)
                .collect(Collectors.groupingBy(r -> r.getCreationDate().format(formatter)));
        
        byMonth.forEach((month, reviews) -> {
            double avg = reviews.stream()
                    .filter(r -> r.getRating() != null)
                    .mapToDouble(Review::getRating)
                    .average()
                    .orElse(0.0);
            monthlyRatings.put(month, roundToTwoDecimals(avg));
        });

        // Category averages
        double avgCleanliness = periodReviews.stream()
                .filter(r -> r.getCleanliness() != null)
                .mapToDouble(Review::getCleanliness)
                .average().orElse(0.0);
        double avgCommunication = periodReviews.stream()
                .filter(r -> r.getCommunication() != null)
                .mapToDouble(Review::getCommunication)
                .average().orElse(0.0);
        double avgLocation = periodReviews.stream()
                .filter(r -> r.getLocation() != null)
                .mapToDouble(Review::getLocation)
                .average().orElse(0.0);
        double avgValue = periodReviews.stream()
                .filter(r -> r.getValue() != null)
                .mapToDouble(Review::getValue)
                .average().orElse(0.0);

        // Rating distribution
        Map<Integer, Long> distribution = periodReviews.stream()
                .filter(r -> r.getRating() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getRating().intValue(),
                        Collectors.counting()
                ));
        // Ensure all ratings 1-5 are present
        for (int i = 1; i <= 5; i++) {
            distribution.putIfAbsent(i, 0L);
        }

        // Best/Worst aspects
        Map<String, Double> aspects = new HashMap<>();
        aspects.put("Cleanliness", avgCleanliness);
        aspects.put("Communication", avgCommunication);
        aspects.put("Location", avgLocation);
        aspects.put("Value", avgValue);

        String bestAspect = aspects.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");
        
        String worstAspect = aspects.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        return RatingAnalyticsDTO.builder()
                .currentAverageRating(roundToTwoDecimals(currentAvg))
                .previousPeriodRating(roundToTwoDecimals(previousPeriodRating))
                .ratingTrend(roundToTwoDecimals(ratingTrend))
                .totalReviews(periodReviews.size())
                .monthlyAverageRatings(monthlyRatings)
                .avgCleanliness(roundToTwoDecimals(avgCleanliness))
                .avgCommunication(roundToTwoDecimals(avgCommunication))
                .avgLocation(roundToTwoDecimals(avgLocation))
                .avgValue(roundToTwoDecimals(avgValue))
                .ratingDistribution(distribution)
                .bestAspect(bestAspect)
                .worstAspect(worstAspect)
                .build();
    }

    // RESERVATION TRENDS ANALYSIS

    /**
     * Calculates reservation trends and booking patterns
     */
    private ReservationTrendsDTO calculateReservationTrends(List<Reservation> reservations, 
                                                            LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating reservation trends");

        if (reservations.isEmpty()) {
            return ReservationTrendsDTO.builder()
                    .avgBookingsPerMonth(0.0)
                    .bookingGrowthRate(0.0)
                    .bookingsByDayOfWeek(new HashMap<>())
                    .bookingsByMonth(new HashMap<>())
                    .avgLeadTimeDays(0.0)
                    .avgStayDuration(0.0)
                    .cancellationRate(0.0)
                    .stayDurationDistribution(new HashMap<>())
                    .peakMonths(new ArrayList<>())
                    .lowSeasonMonths(new ArrayList<>())
                    .build();
        }

        // Filter by date range
        List<Reservation> filteredRes = reservations;
        if (startDate != null && endDate != null) {
            filteredRes = filterReservationsByDateRange(reservations, startDate, endDate);
        }

        // Bookings by day of week (based on check-in date)
        Map<String, Long> byDayOfWeek = new EnumMap<>(DayOfWeek.class).entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> 0L));
        
        byDayOfWeek = filteredRes.stream()
                .filter(r -> r.getDates() != null && r.getDates().getCheckIn() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getDates().getCheckIn().getDayOfWeek().toString(),
                        Collectors.counting()
                ));
        // Ensure all days present
        for (DayOfWeek day : DayOfWeek.values()) {
            byDayOfWeek.putIfAbsent(day.toString(), 0L);
        }

        // Bookings by month
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Long> byMonth = new TreeMap<>(filteredRes.stream()
                .filter(r -> r.getDates() != null && r.getDates().getCheckIn() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getDates().getCheckIn().format(formatter),
                        Collectors.counting()
                )));

        // Average bookings per month
        double avgPerMonth = byMonth.isEmpty() ? 0 : 
                byMonth.values().stream().mapToLong(Long::longValue).average().orElse(0);

        // Booking growth rate (compare first half vs second half of period)
        double growthRate = calculateGrowthRate(byMonth);

        // Lead time analysis (days between creation and check-in)
        // Since we don't have creation date in Reservation, we'll estimate based on check-in patterns
        DoubleSummaryStatistics leadTimeStats = filteredRes.stream()
                .filter(r -> r.getDates() != null && r.getCreatedAt() != null)
                .mapToDouble(r -> ChronoUnit.DAYS.between(
                        r.getCreatedAt().toLocalDate(), 
                        r.getDates().getCheckIn()))
                .filter(d -> d >= 0)
                .summaryStatistics();

        double avgLeadTime = leadTimeStats.getCount() > 0 ? leadTimeStats.getAverage() : 0;
        double minLeadTime = leadTimeStats.getCount() > 0 ? leadTimeStats.getMin() : 0;
        double maxLeadTime = leadTimeStats.getCount() > 0 ? leadTimeStats.getMax() : 0;

        // Stay duration patterns
        DoubleSummaryStatistics stayStats = filteredRes.stream()
                .filter(r -> r.getDates() != null)
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                .mapToDouble(r -> ChronoUnit.DAYS.between(
                        r.getDates().getCheckIn(), 
                        r.getDates().getCheckOut()))
                .filter(d -> d > 0)
                .summaryStatistics();

        double avgStay = stayStats.getCount() > 0 ? stayStats.getAverage() : 0;

        // Stay duration distribution
        Map<String, Long> stayDistribution = filteredRes.stream()
                .filter(r -> r.getDates() != null && !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                .map(r -> ChronoUnit.DAYS.between(r.getDates().getCheckIn(), r.getDates().getCheckOut()))
                .filter(d -> d > 0)
                .collect(Collectors.groupingBy(
                        this::categorizeStayDuration,
                        Collectors.counting()
                ));

        // Cancellation rate
        long totalBookings = filteredRes.size();
        long cancelled = filteredRes.stream()
                .filter(r -> "CANCELLED".equalsIgnoreCase(r.getStatus()))
                .count();
        double cancellationRate = totalBookings > 0 ? (cancelled * 100.0 / totalBookings) : 0;

        // Monthly cancellation rates
        Map<String, Double> monthlyCancellation = new TreeMap<>();
        Map<String, List<Reservation>> resByMonth = filteredRes.stream()
                .filter(r -> r.getDates() != null && r.getDates().getCheckIn() != null)
                .collect(Collectors.groupingBy(r -> r.getDates().getCheckIn().format(formatter)));
        
        resByMonth.forEach((month, list) -> {
            long total = list.size();
            long cancel = list.stream().filter(r -> "CANCELLED".equalsIgnoreCase(r.getStatus())).count();
            monthlyCancellation.put(month, total > 0 ? roundToTwoDecimals(cancel * 100.0 / total) : 0.0);
        });

        // Peak and low season identification
        List<String> peakMonths = identifyPeakMonths(byMonth);
        List<String> lowSeasonMonths = identifyLowSeasonMonths(byMonth);

        return ReservationTrendsDTO.builder()
                .avgBookingsPerMonth(roundToTwoDecimals(avgPerMonth))
                .bookingGrowthRate(roundToTwoDecimals(growthRate))
                .bookingsByDayOfWeek(byDayOfWeek)
                .bookingsByMonth(byMonth)
                .avgLeadTimeDays(roundToTwoDecimals(avgLeadTime))
                .minLeadTimeDays(roundToTwoDecimals(minLeadTime))
                .maxLeadTimeDays(roundToTwoDecimals(maxLeadTime))
                .avgStayDuration(roundToTwoDecimals(avgStay))
                .stayDurationDistribution(stayDistribution)
                .cancellationRate(roundToTwoDecimals(cancellationRate))
                .monthlyCancellationRates(monthlyCancellation)
                .peakMonths(peakMonths)
                .lowSeasonMonths(lowSeasonMonths)
                .build();
    }

    // COMPARATIVE PERFORMANCE ANALYSIS

    /**
     * Calculates comparative performance against similar properties using MongoDB aggregation
     */
    private ComparativePerformanceDTO calculateComparativePerformance(Property property) {
        log.debug("Calculating comparative performance for property: {}", property.getName());

        String city = property.getCity();
        String region = property.getRegion();

        // Find comparable properties in the same city/region
        List<Property> comparableProperties = findComparableProperties(property);
        
        if (comparableProperties.size() <= 1) {
            // Not enough data for comparison
            return ComparativePerformanceDTO.builder()
                    .comparisonScope("Insufficient data for comparison")
                    .propertiesCompared(0)
                    .overallPerformanceScore(0)
                    .performanceCategory("N/A")
                    .build();
        }

        String scope = city != null ? "Same City: " + city : 
                       (region != null ? "Same Region: " + region : "All Properties");

        // Calculate metrics for all comparable properties
        List<PropertyMetrics> allMetrics = comparableProperties.stream()
                .map(this::calculatePropertyMetrics)
                .collect(Collectors.toList());

        // Find this property's metrics
        PropertyMetrics thisPropertyMetrics = allMetrics.stream()
                .filter(m -> m.propertyId.equals(property.getId()))
                .findFirst()
                .orElse(new PropertyMetrics());

        // Calculate market averages
        double avgMarketRevenue = allMetrics.stream().mapToDouble(m -> m.revenue).average().orElse(0);
        double avgMarketOccupancy = allMetrics.stream().mapToDouble(m -> m.occupancy).average().orElse(0);
        double avgMarketRating = allMetrics.stream().filter(m -> m.rating > 0).mapToDouble(m -> m.rating).average().orElse(0);
        double marketAvgPrice = allMetrics.stream().filter(m -> m.avgPrice > 0).mapToDouble(m -> m.avgPrice).average().orElse(0);

        // Calculate percentiles
        double revenuePercentile = calculatePercentile(allMetrics.stream().mapToDouble(m -> m.revenue).toArray(), 
                                                        thisPropertyMetrics.revenue);
        double occupancyPercentile = calculatePercentile(allMetrics.stream().mapToDouble(m -> m.occupancy).toArray(), 
                                                          thisPropertyMetrics.occupancy);
        double ratingPercentile = calculatePercentile(allMetrics.stream().filter(m -> m.rating > 0)
                                                        .mapToDouble(m -> m.rating).toArray(), 
                                                       thisPropertyMetrics.rating);

        // Price positioning
        String pricePositioning = determinePricePositioning(thisPropertyMetrics.avgPrice, marketAvgPrice);

        // Overall performance score (weighted average)
        double performanceScore = calculatePerformanceScore(revenuePercentile, occupancyPercentile, ratingPercentile);
        String performanceCategory = categorizePerformance(performanceScore);

        return ComparativePerformanceDTO.builder()
                .comparisonScope(scope)
                .propertiesCompared(comparableProperties.size() - 1) // Exclude self
                .propertyRevenue(roundToTwoDecimals(thisPropertyMetrics.revenue))
                .avgMarketRevenue(roundToTwoDecimals(avgMarketRevenue))
                .revenuePercentile(roundToTwoDecimals(revenuePercentile))
                .propertyOccupancy(roundToTwoDecimals(thisPropertyMetrics.occupancy))
                .avgMarketOccupancy(roundToTwoDecimals(avgMarketOccupancy))
                .occupancyPercentile(roundToTwoDecimals(occupancyPercentile))
                .propertyRating(roundToTwoDecimals(thisPropertyMetrics.rating))
                .avgMarketRating(roundToTwoDecimals(avgMarketRating))
                .ratingPercentile(roundToTwoDecimals(ratingPercentile))
                .propertyAvgPrice(roundToTwoDecimals(thisPropertyMetrics.avgPrice))
                .marketAvgPrice(roundToTwoDecimals(marketAvgPrice))
                .pricePositioning(pricePositioning)
                .overallPerformanceScore(roundToTwoDecimals(performanceScore))
                .performanceCategory(performanceCategory)
                .build();
    }

    // MONGODB AGGREGATION PIPELINE

    /**
     * Uses MongoDB aggregation pipeline for efficient revenue calculation
     */
    private AnalyticsResponseDTO calculateAggregatedAnalyticsWithPipeline(List<Property> properties, 
                                                                           LocalDate startDate, LocalDate endDate,
                                                                           String managerId) {
        log.info("Using MongoDB aggregation pipeline for analytics calculation");

        // Collect all room IDs
        List<String> allRoomIds = properties.stream()
                .filter(p -> p.getRooms() != null)
                .flatMap(p -> p.getRooms().stream())
                .map(Room::getId)
                .collect(Collectors.toList());

        if (allRoomIds.isEmpty()) {
            return AnalyticsResponseDTO.builder()
                    .propertyName("All Properties (Aggregated)")
                    .periodStart(startDate)
                    .periodEnd(endDate)
                    .totalReservations(0)
                    .totalRevenue(0.0)
                    .build();
        }

        // Build aggregation pipeline
        Criteria criteria = Criteria.where("roomId").in(allRoomIds);
        if (startDate != null && endDate != null) {
            criteria = criteria.and("dates.checkIn").gte(startDate).lte(endDate);
        }

        MatchOperation matchStage = Aggregation.match(criteria);

        GroupOperation groupStage = Aggregation.group()
                .count().as("totalReservations")
                .sum(ConditionalOperators.when(Criteria.where("status").is("CONFIRMED")).then(1).otherwise(0)).as("confirmed")
                .sum(ConditionalOperators.when(Criteria.where("status").is("CANCELLED")).then(1).otherwise(0)).as("cancelled")
                .sum(ConditionalOperators.when(Criteria.where("status").is("COMPLETED")).then(1).otherwise(0)).as("completed")
                .sum("adults").as("totalAdults")
                .sum("children").as("totalChildren");

        Aggregation aggregation = Aggregation.newAggregation(matchStage, groupStage);
        
        AggregationResults<AggregatedResult> results = mongoTemplate.aggregate(
                aggregation, "reservations", AggregatedResult.class);

        AggregatedResult result = results.getUniqueMappedResult();

        // Calculate revenue (needs room price info, so done separately)
        List<Reservation> allReservations = reservationRepository.findByRoomIdIn(allRoomIds);
        if (startDate != null && endDate != null) {
            allReservations = filterReservationsByDateRange(allReservations, startDate, endDate);
        }
        double totalRevenue = calculateTotalRevenue(allReservations, properties);

        long totalRes = result != null ? result.getTotalReservations() : 0;
        long confirmed = result != null ? result.getConfirmed() : 0;
        long cancelled = result != null ? result.getCancelled() : 0;
        long completed = result != null ? result.getCompleted() : 0;
        long totalAdults = result != null ? result.getTotalAdults() : 0;
        long totalChildren = result != null ? result.getTotalChildren() : 0;

        return AnalyticsResponseDTO.builder()
                .propertyName("All Properties (Aggregated)")
                .periodStart(startDate)
                .periodEnd(endDate)
                .totalReservations(totalRes)
                .confirmedReservations(confirmed)
                .cancelledReservations(cancelled)
                .completedReservations(completed)
                .totalRevenue(roundToTwoDecimals(totalRevenue))
                .averageRevenuePerReservation(totalRes - cancelled > 0 ? 
                        roundToTwoDecimals(totalRevenue / (totalRes - cancelled)) : 0)
                .totalGuests(totalAdults + totalChildren)
                .totalAdults(totalAdults)
                .totalChildren(totalChildren)
                .build();
    }

    // HELPER CLASSES

    private static class BasicStats {
        long totalReservations;
        long confirmedReservations;
        long cancelledReservations;
        long completedReservations;
        double totalRevenue;
        double avgRevenuePerReservation;
        long totalNights;
        long totalAdults;
        long totalChildren;
    }

    private static class PropertyMetrics {
        String propertyId;
        double revenue;
        double occupancy;
        double rating;
        double avgPrice;
    }

    // DTO for MongoDB aggregation results
    @lombok.Data
    public static class AggregatedResult {
        private long totalReservations;
        private long confirmed;
        private long cancelled;
        private long completed;
        private long totalAdults;
        private long totalChildren;
    }

    // HELPER METHODS

    private BasicStats calculateBasicStats(List<Reservation> reservations, Property property) {
        BasicStats stats = new BasicStats();
        
        stats.totalReservations = reservations.size();
        stats.confirmedReservations = reservations.stream()
                .filter(r -> "CONFIRMED".equalsIgnoreCase(r.getStatus())).count();
        stats.cancelledReservations = reservations.stream()
                .filter(r -> "CANCELLED".equalsIgnoreCase(r.getStatus())).count();
        stats.completedReservations = reservations.stream()
                .filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus())).count();

        for (Reservation res : reservations) {
            if ("CANCELLED".equalsIgnoreCase(res.getStatus())) continue;

            Room room = findRoomById(property, res.getRoomId());
            if (room != null && res.getDates() != null) {
                long nights = ChronoUnit.DAYS.between(res.getDates().getCheckIn(), res.getDates().getCheckOut());
                if (nights < 1) nights = 1;
                stats.totalNights += nights;

                double priceAdults = room.getPricePerNightAdults() != null ? room.getPricePerNightAdults() : 0.0;
                double priceChildren = room.getPricePerNightChildren() != null ? room.getPricePerNightChildren() : 0.0;
                stats.totalRevenue += (res.getAdults() * priceAdults + res.getChildren() * priceChildren) * nights;
            }

            stats.totalAdults += res.getAdults();
            stats.totalChildren += res.getChildren();
        }

        long nonCancelled = stats.totalReservations - stats.cancelledReservations;
        stats.avgRevenuePerReservation = nonCancelled > 0 ? 
                roundToTwoDecimals(stats.totalRevenue / nonCancelled) : 0;

        return stats;
    }

    private List<Reservation> filterReservationsByDateRange(List<Reservation> reservations, 
                                                             LocalDate startDate, LocalDate endDate) {
        return reservations.stream()
                .filter(r -> r.getDates() != null)
                .filter(r -> !r.getDates().getCheckIn().isBefore(startDate) && 
                             !r.getDates().getCheckOut().isAfter(endDate.plusDays(1)))
                .collect(Collectors.toList());
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
                .filter(r -> r.getDates() != null)
                .mapToLong(r -> {
                    LocalDate checkIn = r.getDates().getCheckIn().isBefore(startDate) ? startDate : r.getDates().getCheckIn();
                    LocalDate checkOut = r.getDates().getCheckOut().isAfter(endDate) ? endDate : r.getDates().getCheckOut();
                    return ChronoUnit.DAYS.between(checkIn, checkOut);
                })
                .filter(d -> d > 0)
                .sum();

        return totalAvailableNights > 0 ? (bookedNights * 100.0 / totalAvailableNights) : 0.0;
    }

    private List<RoomAnalyticsDTO> calculateRoomAnalytics(Property property, List<Reservation> reservations,
                                                           LocalDate startDate, LocalDate endDate) {
        if (property.getRooms() == null) return new ArrayList<>();

        long totalDays = (startDate != null && endDate != null) ? 
                ChronoUnit.DAYS.between(startDate, endDate) : 365;
        if (totalDays <= 0) totalDays = 1;

        final long finalTotalDays = totalDays;

        return property.getRooms().stream().map(room -> {
            List<Reservation> roomReservations = reservations.stream()
                    .filter(r -> room.getId().equals(r.getRoomId()) && !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                    .collect(Collectors.toList());

            double revenue = 0.0;
            long bookedNights = 0;
            
            for (Reservation res : roomReservations) {
                if (res.getDates() == null) continue;
                
                long nights = ChronoUnit.DAYS.between(res.getDates().getCheckIn(), res.getDates().getCheckOut());
                if (nights < 1) nights = 1;
                bookedNights += nights;
                
                double priceAdults = room.getPricePerNightAdults() != null ? room.getPricePerNightAdults() : 0.0;
                double priceChildren = room.getPricePerNightChildren() != null ? room.getPricePerNightChildren() : 0.0;
                revenue += (res.getAdults() * priceAdults + res.getChildren() * priceChildren) * nights;
            }

            double occupancy = finalTotalDays > 0 ? (bookedNights * 100.0 / finalTotalDays) : 0;

            return RoomAnalyticsDTO.builder()
                    .roomId(room.getId())
                    .roomName(room.getName())
                    .reservationCount(roomReservations.size())
                    .revenue(roundToTwoDecimals(revenue))
                    .occupancyRate(roundToTwoDecimals(occupancy))
                    .build();
        }).collect(Collectors.toList());
    }

    private Map<String, MonthlyStatsDTO> calculateMonthlyBreakdown(List<Reservation> reservations, Property property) {
        Map<String, MonthlyStatsDTO> breakdown = new TreeMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (Reservation res : reservations) {
            if ("CANCELLED".equalsIgnoreCase(res.getStatus()) || res.getDates() == null) continue;

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
                    MonthlyStatsDTO.builder()
                            .reservations(1)
                            .revenue(resRevenue)
                            .guests(guests)
                            .build(),
                    (existing, newVal) -> MonthlyStatsDTO.builder()
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
            if ("CANCELLED".equalsIgnoreCase(res.getStatus()) || res.getDates() == null) continue;

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

        return total;
    }

    private List<Property> findComparableProperties(Property property) {
        // First try same city, then same region, then all
        if (property.getCity() != null) {
            List<Property> sameCity = propertyRepository.findAll().stream()
                    .filter(p -> property.getCity().equalsIgnoreCase(p.getCity()))
                    .collect(Collectors.toList());
            if (sameCity.size() >= 3) return sameCity;
        }
        
        if (property.getRegion() != null) {
            List<Property> sameRegion = propertyRepository.findAll().stream()
                    .filter(p -> property.getRegion().equalsIgnoreCase(p.getRegion()))
                    .collect(Collectors.toList());
            if (sameRegion.size() >= 3) return sameRegion;
        }
        
        // Fall back to all properties
        return propertyRepository.findAll();
    }

    private PropertyMetrics calculatePropertyMetrics(Property property) {
        PropertyMetrics metrics = new PropertyMetrics();
        metrics.propertyId = property.getId();

        List<String> roomIds = getRoomIds(property);
        List<Reservation> reservations = roomIds.isEmpty() ? 
                new ArrayList<>() : reservationRepository.findByRoomIdIn(roomIds);

        // Revenue (last 12 months)
        LocalDate yearAgo = LocalDate.now().minusYears(1);
        List<Reservation> yearReservations = reservations.stream()
                .filter(r -> r.getDates() != null && !r.getDates().getCheckIn().isBefore(yearAgo))
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());

        metrics.revenue = calculateRevenueForReservations(yearReservations, property);

        // Occupancy
        metrics.occupancy = calculateOccupancyRate(property, yearReservations, yearAgo, LocalDate.now());

        // Rating
        List<Review> reviews = reviewRepository.findByPropertyId(property.getId());
        metrics.rating = reviews.stream()
                .filter(r -> r.getRating() != null)
                .mapToDouble(Review::getRating)
                .average()
                .orElse(0.0);

        // Average price
        if (property.getRooms() != null && !property.getRooms().isEmpty()) {
            metrics.avgPrice = property.getRooms().stream()
                    .filter(r -> r.getPricePerNightAdults() != null)
                    .mapToDouble(Room::getPricePerNightAdults)
                    .average()
                    .orElse(0.0);
        }

        return metrics;
    }

    private double calculateRevenueForReservations(List<Reservation> reservations, Property property) {
        double total = 0.0;
        for (Reservation res : reservations) {
            if (res.getDates() == null) continue;
            
            Room room = findRoomById(property, res.getRoomId());
            if (room != null) {
                long nights = ChronoUnit.DAYS.between(res.getDates().getCheckIn(), res.getDates().getCheckOut());
                if (nights < 1) nights = 1;
                double priceAdults = room.getPricePerNightAdults() != null ? room.getPricePerNightAdults() : 0.0;
                double priceChildren = room.getPricePerNightChildren() != null ? room.getPricePerNightChildren() : 0.0;
                total += (res.getAdults() * priceAdults + res.getChildren() * priceChildren) * nights;
            }
        }
        return total;
    }

    private double calculatePercentile(double[] values, double value) {
        if (values.length == 0) return 0;
        
        Arrays.sort(values);
        int count = 0;
        for (double v : values) {
            if (v < value) count++;
        }
        return (count * 100.0) / values.length;
    }

    private String determinePricePositioning(double propertyPrice, double marketAvg) {
        if (propertyPrice <= 0 || marketAvg <= 0) return "Unknown";
        
        double ratio = propertyPrice / marketAvg;
        if (ratio < 0.8) return "Budget";
        if (ratio <= 1.2) return "Mid-Range";
        return "Premium";
    }

    private double calculatePerformanceScore(double revenuePerc, double occupancyPerc, double ratingPerc) {
        // Weighted average: Revenue 40%, Occupancy 35%, Rating 25%
        return (revenuePerc * 0.4) + (occupancyPerc * 0.35) + (ratingPerc * 0.25);
    }

    private String categorizePerformance(double score) {
        if (score >= 75) return "Top Performer";
        if (score >= 50) return "Above Average";
        if (score >= 25) return "Average";
        return "Needs Improvement";
    }

    private double calculateGrowthRate(Map<String, Long> monthlyData) {
        if (monthlyData.size() < 2) return 0;
        
        List<Long> values = new ArrayList<>(monthlyData.values());
        int mid = values.size() / 2;
        
        double firstHalf = values.subList(0, mid).stream().mapToLong(Long::longValue).average().orElse(0);
        double secondHalf = values.subList(mid, values.size()).stream().mapToLong(Long::longValue).average().orElse(0);
        
        return firstHalf > 0 ? ((secondHalf - firstHalf) / firstHalf) * 100 : 0;
    }

    private String categorizeStayDuration(long nights) {
        if (nights <= 2) return "1-2 nights";
        if (nights <= 5) return "3-5 nights";
        if (nights <= 7) return "6-7 nights";
        if (nights <= 14) return "1-2 weeks";
        return "2+ weeks";
    }

    private List<String> identifyPeakMonths(Map<String, Long> monthlyData) {
        if (monthlyData.isEmpty()) return new ArrayList<>();
        
        double avg = monthlyData.values().stream().mapToLong(Long::longValue).average().orElse(0);
        double threshold = avg * 1.25; // 25% above average = peak
        
        return monthlyData.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> identifyLowSeasonMonths(Map<String, Long> monthlyData) {
        if (monthlyData.isEmpty()) return new ArrayList<>();
        
        double avg = monthlyData.values().stream().mapToLong(Long::longValue).average().orElse(0);
        double threshold = avg * 0.75; // 25% below average = low season
        
        return monthlyData.entrySet().stream()
                .filter(e -> e.getValue() <= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Calculates the most frequently booked room type
     */
    private String calculateMostBookedRoomType(List<Reservation> reservations, Property property) {
        if (reservations.isEmpty() || property.getRooms() == null) {
            return "N/A";
        }

        // Count bookings by room type
        Map<String, Long> roomTypeCount = reservations.stream()
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                .map(r -> {
                    Room room = findRoomById(property, r.getRoomId());
                    return room != null ? room.getRoomType() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()));

        // Find the room type with max bookings
        return roomTypeCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");
    }

    /**
     * Calculates the average number of guests per room per booking
     */
    private double calculateAvgGuestsPerRoomPerBooking(List<Reservation> reservations) {
        List<Reservation> validReservations = reservations.stream()
                .filter(r -> !"CANCELLED".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());

        if (validReservations.isEmpty()) {
            return 0.0;
        }

        // Each reservation is for one room, so avg guests = total guests / total bookings
        double totalGuests = validReservations.stream()
                .mapToDouble(r -> r.getAdults() + r.getChildren())
                .sum();

        return totalGuests / validReservations.size();
    }

    private List<String> getRoomIds(Property property) {
        return property.getRooms() != null 
                ? property.getRooms().stream().map(Room::getId).collect(Collectors.toList())
                : new ArrayList<>();
    }

    private Room findRoomById(Property property, String roomId) {
        if (property.getRooms() == null) return null;
        return property.getRooms().stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .orElse(null);
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private RegisteredUser getManagerFromToken(String token) {
        String cleanToken = token.replace("Bearer ", "");
        String userId = jwtUtil.getUserIdFromToken(cleanToken);
        
        RegisteredUser user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found for token");
                    return new IllegalArgumentException("User not found");
                });

        if (!"MANAGER".equalsIgnoreCase(user.getRole())) {
            log.warn("Non-manager user {} attempted to access analytics", userId);
            throw new SecurityException("Access Denied: Only managers can view analytics.");
        }

        return user;
    }
}
