package largebeb.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ManagerReservationDTO {
    
    // Reservation Info
    private String reservationId;
    private String status;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private LocalDateTime createdAt;
    
    // Guest Info
    private int adults;
    private int children;
    private String guestId;
    private String guestName;
    private String guestEmail;
    
    // Property & Room Info
    private String propertyId;
    private String propertyName;
    private String roomId;
    private String roomName;
    private String roomType;
    
    // Financial Info
    private double totalPrice;
}
