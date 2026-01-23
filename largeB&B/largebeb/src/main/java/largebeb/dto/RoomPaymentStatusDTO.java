package largebeb.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class RoomPaymentStatusDTO {
    
    private String propertyId;
    private String propertyName;
    private List<RoomStatusDetail> rooms;
    
    @Data
    @Builder
    public static class RoomStatusDetail {
        private String roomId;
        private String roomName;
        private String roomType;
        private String availabilityStatus; // "available", "maintenance"
        
        // Current booking info (if any)
        private boolean currentlyOccupied;
        private String currentReservationId;
        private String currentGuestId;
        private LocalDate currentCheckIn;
        private LocalDate currentCheckOut;
        private String paymentStatus; // "CONFIRMED", "PENDING_PAYMENT", "COMPLETED"
        
        // Upcoming reservations count
        private long upcomingReservations;
        
        // Revenue info
        private double totalRevenueGenerated;
        private long totalCompletedBookings;
    }
}
