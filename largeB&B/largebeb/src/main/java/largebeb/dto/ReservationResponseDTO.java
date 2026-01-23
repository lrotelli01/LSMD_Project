package largebeb.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class ReservationResponseDTO {
    private String id;              // Reservation ID
    private String roomName; 
    private String propertyId;    // Keep ID for linking to details 
    private String roomId;          // Keep ID for linking to details
    private LocalDate checkIn;
    private LocalDate checkOut;
    private String status;          // CONFIRMED, CANCELLED
    private int adults;
    private int children;
    private double totalPrice;      // Useful for history
    private String mainImage;       // To show a thumbnail in the list

    private String message;         // Additional info or error messages
}