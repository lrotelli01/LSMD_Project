package largebeb.controllers;

import largebeb.dto.ReviewRequestDTO;
import largebeb.dto.ReviewResponseDTO;
import largebeb.services.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // Helper method to extract Bearer token
    private String extractToken(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    // Customer create the review
    @PostMapping
    public ResponseEntity<?> createReview(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ReviewRequestDTO request) {
        
        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            // Pass the raw token to the service
            ReviewResponseDTO response = reviewService.createReview(token, request);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(e.getMessage()); // Logic error
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage()); // Not found
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Customer edit the review
    @PutMapping("/{reviewId}")
    public ResponseEntity<?> editReview(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String reviewId, 
            @Valid @RequestBody ReviewRequestDTO request) {

        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            ReviewResponseDTO response = reviewService.editReview(token, reviewId, request);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage()); // Forbidden (not your review)
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Manager Reply
    @PatchMapping("/{reviewId}/reply")
    public ResponseEntity<?> replyToReview(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String reviewId, 
            @RequestBody ReviewRequestDTO request) {

        String token = extractToken(authHeader);
        if (token == null) return ResponseEntity.badRequest().body("Invalid Token");

        try {
            String replyText = request.getManagerReply();
            ReviewResponseDTO response = reviewService.replyToReview(token, reviewId, replyText);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage()); // Forbidden (not manager)
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Get all the reviews of the specified property
    @GetMapping("/property/{propertyId}")
    public ResponseEntity<?> getReviewsByPropertyId(@PathVariable String propertyId) {
        try {
            List<ReviewResponseDTO> reviews = reviewService.getReviewsByPropertyId(propertyId);
            return ResponseEntity.ok(reviews);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Delete review
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<?> deleteReview(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String reviewId) {

        String token = extractToken(authHeader);
        if (token == null) {
            return ResponseEntity.badRequest().body("Invalid Token");
        }

        try {
            reviewService.deleteReview(token, reviewId);
            return ResponseEntity.ok("Review deleted successfully.");

        } catch (SecurityException e) {
            // 403 Forbidden: User is not the owner of the review
            return ResponseEntity.status(403).body(e.getMessage());

        } catch (IllegalArgumentException e) {
            // 404 Not Found: Review ID does not exist
            return ResponseEntity.status(404).body(e.getMessage());

        } catch (Exception e) {
            // 500 or 400 for generic errors
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

}