package largebeb.services;

import largebeb.dto.ReviewRequestDTO;
import largebeb.dto.ReviewResponseDTO;
import largebeb.model.Property;
import largebeb.model.Reservation;
import largebeb.model.Review;
import largebeb.model.RegisteredUser; 
import largebeb.repository.PropertyRepository;
import largebeb.repository.ReservationRepository;
import largebeb.repository.ReviewRepository;
import largebeb.repository.UserRepository;
import largebeb.utilities.JwtUtil; 
import largebeb.utilities.RatingStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final PropertyRepository propertyRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil; // Injected to handle Token logic

    // Customer create a review
    public ReviewResponseDTO createReview(String token, ReviewRequestDTO request) {
        RegisteredUser currentUser = getUserFromToken(token);

        // Check role
        if (!"CUSTOMER".equalsIgnoreCase(currentUser.getRole())) {
            throw new SecurityException("Only customers can write reviews.");
        }

        // Validate Ratings (Mandatory and 1-5)
        validateCreationRatings(request);

        String targetReservationId = request.getReservationId();

        // Check if a review already exists for this reservation
        if (reviewRepository.findByReservationId(targetReservationId).isPresent()) {
            throw new IllegalStateException("You have already reviewed this stay.");
        }

        // Validate that this reservation exists
        Reservation reservation = reservationRepository.findById(targetReservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        // Null safety and date check
        if (reservation.getDates() == null || reservation.getDates().getCheckOut() == null) {
            throw new IllegalStateException("Reservation date information is missing or corrupt.");
        }

        if (LocalDate.now().isBefore(reservation.getDates().getCheckOut())) {
            throw new IllegalStateException("You cannot review a stay until after the check-out date: " 
                + reservation.getDates().getCheckOut());
        }
        // Reservation status check
        if (!"COMPLETED".equalsIgnoreCase(reservation.getStatus())) {
            throw new IllegalStateException("Only completed reservations can be reviewed.");
        }
        
        // Validate that this reservation actually belongs to this customer (ID check)
        if (!reservation.getUserId().equals(currentUser.getId())) {
             throw new SecurityException("You can only review your own reservations.");
        }

        // Find Property immediately (needed to link propertyId)
        Property property = propertyRepository.findByRoomsId(reservation.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Property not found for this room"));

        // Create Review
        Review review = new Review();
        review.setReservationId(targetReservationId);
        review.setPropertyId(property.getId()); // Store propertyId for performance
        review.setCreationDate(LocalDate.now());
        review.setUserId(currentUser.getId());
        review.setReservationId(targetReservationId);
        // Map Customer Fields
        review.setText(request.getText());
        
        // Set Mandatory Ratings
        review.setRating(request.getRating().longValue()); 
        
        review.setCleanliness(request.getCleanliness());
        review.setCommunication(request.getCommunication());
        review.setLocation(request.getLocation());
        review.setValue(request.getValue());

        // Force managerReply to null on creation
        review.setManagerReply(null); 

        Review savedReview = reviewRepository.save(review);

        // Update Property Stats and Latest Reviews
        updateAndSavePropertyStats(property);

        return mapToDTO(savedReview);
    }

    // Customer edit their own review
    public ReviewResponseDTO editReview(String token, String reviewId, ReviewRequestDTO request) {
        RegisteredUser currentUser = getUserFromToken(token);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Prevent edit if manager has replied
        if (review.getManagerReply() != null) {
            throw new IllegalStateException("Cannot edit review after manager has replied.");
        }

        // Only the original author (Customer) can edit
        Reservation reservation = reservationRepository.findById(review.getReservationId())
                .orElseThrow(() -> new IllegalArgumentException("Reservation associated with this review was not found"));

        if (!reservation.getUserId().equals(currentUser.getId())) {
            throw new SecurityException("You can only edit your own reviews.");
        }
        // Reservation status check
        if (!"COMPLETED".equalsIgnoreCase(reservation.getStatus())) {
            throw new IllegalStateException("Only completed reservations can be reviewed.");
        }

        // Validate inputs (Validate the Double values from request directly)
        validatePartialRatings(request.getRating());
        validatePartialRatings(request.getCleanliness());
        validatePartialRatings(request.getCommunication());
        validatePartialRatings(request.getLocation());
        validatePartialRatings(request.getValue());

        // Map only Customer Fields (Ignore managerReply)
        if (request.getText() != null) review.setText(request.getText());
        
        if (request.getRating() != null) review.setRating(request.getRating().longValue());
        
        if (request.getCleanliness() != null) review.setCleanliness(request.getCleanliness());
        if (request.getCommunication() != null) review.setCommunication(request.getCommunication());
        if (request.getLocation() != null) review.setLocation(request.getLocation());
        if (request.getValue() != null) review.setValue(request.getValue());

        Review updatedReview = reviewRepository.save(review);

        // Update Property Stats and Latest Reviews
        updatePropertyStatsByPropertyId(review.getPropertyId());

        return mapToDTO(updatedReview);
    }

    // Customer delete their review
    public void deleteReview(String token, String reviewId) {
        RegisteredUser currentUser = getUserFromToken(token);
        
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        if (currentUser.getRole().equals("MANAGER")) {
            throw new SecurityException("Managers cannot delete reviews.");
        }
        if (!review.getUserId().equals(currentUser.getId())) {
            throw new SecurityException("You can only delete your own reviews.");
        }

        String propertyId = review.getPropertyId();
        reviewRepository.delete(review);

        // Update Property Stats and Latest Reviews (Recalculate without the deleted review)
        updatePropertyStatsByPropertyId(propertyId);
    }

    // Manager reply to review
    public ReviewResponseDTO replyToReview(String token, String reviewId, String replyText) {
        RegisteredUser currentUser = getUserFromToken(token);

        // Check role
        if (!"MANAGER".equalsIgnoreCase(currentUser.getRole())) {
            throw new SecurityException("Only managers can reply to reviews.");
        }
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Retrieve property using the ID in the review
        Property property = propertyRepository.findById(review.getPropertyId())
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));
        
        // Verify Ownership (Manager ID check)
        if (!property.getManagerId().equals(currentUser.getId())) {
            throw new SecurityException("You can only reply to reviews of your own properties.");
        }

        if (review.getManagerReply() != null && !review.getManagerReply().trim().isEmpty()) {
            throw new IllegalStateException("You have already replied to this review.");
        }

        // Map only managerReply
        review.setManagerReply(replyText);

        Review updatedReview = reviewRepository.save(review);

        // Update Property Stats and Latest Reviews 
        // (Necessary because latestReviews is embedded in Property, so we need to update the reply text inside the Property document)
        updateAndSavePropertyStats(property);

        return mapToDTO(updatedReview);
    }

    // Get all the reviews from the specified property
    public List<ReviewResponseDTO> getReviewsByPropertyId(String propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        if (property.getRooms() == null || property.getRooms().isEmpty()) {
            return List.of();
        }

        // Find reviews for these reservations
        List<Review> reviews = reviewRepository.findByPropertyId(propertyId);

        return reviews.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * Get reviews for a property within a specific time period (Manager requirement)
     * Allows manager to see reviews received during a selected time period
     */
    public List<ReviewResponseDTO> getReviewsByPropertyIdAndPeriod(String token, String propertyId, 
                                                                    LocalDate startDate, LocalDate endDate) {
        RegisteredUser manager = getUserFromToken(token);

        // Verify user is a manager
        if (!"MANAGER".equalsIgnoreCase(manager.getRole())) {
            throw new SecurityException("Only managers can access this endpoint.");
        }

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only view reviews of your own properties.");
        }

        List<Review> reviews;
        if (startDate != null && endDate != null) {
            // Use date range filter
            reviews = reviewRepository.findByPropertyIdAndCreationDateBetween(propertyId, startDate, endDate);
        } else {
            // No date filter - return all reviews
            reviews = reviewRepository.findByPropertyId(propertyId);
        }

        // Sort by creation date descending (most recent first)
        return reviews.stream()
                .sorted(Comparator.comparing(Review::getCreationDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all reviews for all properties owned by the manager within a time period
     */
    public List<ReviewResponseDTO> getAllManagerReviewsByPeriod(String token, LocalDate startDate, LocalDate endDate) {
        RegisteredUser manager = getUserFromToken(token);

        // Verify user is a manager
        if (!"MANAGER".equalsIgnoreCase(manager.getRole())) {
            throw new SecurityException("Only managers can access this endpoint.");
        }

        // Get all manager's properties
        List<Property> managerProperties = propertyRepository.findByManagerId(manager.getId());
        
        if (managerProperties.isEmpty()) {
            return List.of();
        }

        // Get all property IDs
        List<String> propertyIds = managerProperties.stream()
                .map(Property::getId)
                .collect(Collectors.toList());

        // Get all reviews for these properties
        List<Review> allReviews = reviewRepository.findByPropertyIdIn(propertyIds);

        // Filter by date if provided
        if (startDate != null && endDate != null) {
            allReviews = allReviews.stream()
                    .filter(r -> r.getCreationDate() != null)
                    .filter(r -> !r.getCreationDate().isBefore(startDate) && !r.getCreationDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Sort by creation date descending
        return allReviews.stream()
                .sorted(Comparator.comparing(Review::getCreationDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Helper methods

    private void validateCreationRatings(ReviewRequestDTO request) {
        // Check for NULLs (Mandatory)
        if (request.getRating() == null || 
            request.getCleanliness() == null || 
            request.getCommunication() == null || 
            request.getLocation() == null || 
            request.getValue() == null) {
            throw new IllegalArgumentException("All rating categories (rating, cleanliness, communication, location, value) are mandatory.");
        }

        // Check Range (1-5)
        if (isInvalidRating(request.getRating()) || 
            isInvalidRating(request.getCleanliness()) || 
            isInvalidRating(request.getCommunication()) || 
            isInvalidRating(request.getLocation()) || 
            isInvalidRating(request.getValue())) {
            throw new IllegalArgumentException("All ratings must be between 1.0 and 5.0.");
        }
    }

    private void validatePartialRatings(Double rating) {
        if (rating != null && isInvalidRating(rating)) {
            throw new IllegalArgumentException("Ratings must be between 1.0 and 5.0.");
        }
    }

    private boolean isInvalidRating(Double rating) {
        return rating < 1.0 || rating > 5.0;
    } 

    private void updatePropertyStatsByPropertyId(String propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found during stats update"));
        updateAndSavePropertyStats(property);
    }

    private void updateAndSavePropertyStats(Property property) {
        // Fetch all reviews for this property
        List<Review> reviews = reviewRepository.findByPropertyId(property.getId());

        if (reviews.isEmpty()) {
            // Reset stats if no reviews exist
            if (property.getRatingStats() != null) {
                property.getRatingStats().setCleanliness(0.0);
                property.getRatingStats().setCommunication(0.0);
                property.getRatingStats().setLocation(0.0);
                property.getRatingStats().setValue(0.0);
            }
            // Clear latest reviews
            property.setLatestReviews(new ArrayList<>());
        } else {
            // Calculate Averages
            double avgClean = reviews.stream().mapToDouble(Review::getCleanliness).average().orElse(0.0);
            double avgComm = reviews.stream().mapToDouble(Review::getCommunication).average().orElse(0.0);
            double avgLoc = reviews.stream().mapToDouble(Review::getLocation).average().orElse(0.0);
            double avgVal = reviews.stream().mapToDouble(Review::getValue).average().orElse(0.0);

            // Ensure ratingStats object exists
            if (property.getRatingStats() == null) {
                 property.setRatingStats(new RatingStats());
            }
            
            property.getRatingStats().setCleanliness(round(avgClean));
            property.getRatingStats().setCommunication(round(avgComm));
            property.getRatingStats().setLocation(round(avgLoc));
            property.getRatingStats().setValue(round(avgVal));
            
            // Update Latest Reviews (Top 10 sorted by creationDate descending)
            List<Review> latestReviews = reviews.stream()
                    .sorted(Comparator.comparing(Review::getCreationDate).reversed())
                    .limit(10)
                    .collect(Collectors.toList());

            property.setLatestReviews(latestReviews);
        }

        // Save Property Document
        propertyRepository.save(property);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private RegisteredUser getUserFromToken(String token) {
        // Extract userId from the token
        String userId = jwtUtil.getUserIdFromToken(token);
        
        // Find user by ID
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
    }

    private ReviewResponseDTO mapToDTO(Review review) {
        return new ReviewResponseDTO(
            review.getId(),
            review.getReservationId(),
            review.getUserId(),
            review.getCreationDate(), 
            review.getText(),
            review.getRating() != null ? review.getRating() : 0L, 
            review.getCleanliness(),
            review.getCommunication(),
            review.getLocation(),
            review.getValue(),
            review.getManagerReply()
        );
    }
}