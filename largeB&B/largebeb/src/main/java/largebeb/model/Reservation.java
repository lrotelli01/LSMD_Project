package largebeb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reservations")
public class Reservation {

    @Id
    private String id;

    private Integer adults;
    private Integer children;

    private String status; 

    private String userId;
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