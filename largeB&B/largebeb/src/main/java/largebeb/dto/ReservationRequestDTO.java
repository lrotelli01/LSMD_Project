package largebeb.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReservationRequestDTO {    
    @NotBlank(message = "Room Id is required")
    private String roomId;

    @NotBlank(message = "Property Id is required")
    private String propertyId;

    @NotNull(message = "Check-in date is required")
    @FutureOrPresent(message = "Check-in must be today or in the future")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate checkIn;

    @NotNull(message = "Check-out date is required")
    @Future(message = "Check-out must be in the future")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate checkOut;

    @Min(value = 1, message = "At least 1 adult is required")
    private Integer adults;

    @Min(value = 0, message = "Children cannot be negative")
    private Integer children;
}