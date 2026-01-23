package largebeb.utilities;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethod {

    private String id;           // Internal ID of the saved method
    private String cardType;     // e.g., "VISA", "MASTERCARD", "PAYPAL"
    private String last4Digits;  // e.g., "4242" (ONLY the last 4 digits)
    private String expiryDate;   // e.g., "12/25"
    private String cardHolderName; // "John Doe"
    private String gatewayToken; // Token from payment gateway for future charges
    
}