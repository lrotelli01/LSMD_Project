package largebeb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentMethodResponseDTO {
    private String id;
    private String cardType;
    private String last4Digits;
    private String expiryDate;
    private String cardHolderName;
}