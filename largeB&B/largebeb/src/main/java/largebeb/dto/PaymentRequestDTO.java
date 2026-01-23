package largebeb.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class PaymentRequestDTO {
    @NotBlank(message = "Temporary Reservation ID is required")
    private String tempReservationId;
}