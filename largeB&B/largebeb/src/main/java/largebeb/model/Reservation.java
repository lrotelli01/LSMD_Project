package largebeb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reservations")
// AVAILABILITY CHECK INDEX (CRITICAL)
// Optimizes the query: "Is Room X free between Date A and Date B?"
@CompoundIndex(name = "room_availability_idx", def = "{'roomId': 1, 'dates.checkIn': 1, 'dates.checkOut': 1}")
public class Reservation {

    @Id
    private String id;

    private Integer adults;
    private Integer children;

    private String status; 

    // USER HISTORY INDEX
    // Fast lookup for "My Bookings" page
    @Indexed
    private String userId;

    // Indexed as part of the Compound Index above, but also useful alone
    @Indexed
    private String roomId;
    
    // Nested object for 'dates'
    private ReservationDates dates;

    @CreatedDate // Automatically sets the date when saved
    private LocalDateTime createdAt;

    // Inner Class for the 'dates' Object
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReservationDates {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate checkIn; 

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate checkOut;
    }
}