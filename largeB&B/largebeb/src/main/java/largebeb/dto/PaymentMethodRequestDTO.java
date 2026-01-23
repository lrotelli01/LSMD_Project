package largebeb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentMethodRequestDTO {

    @NotBlank(message = "Card type is required (e.g., VISA, AMEX)")
    private String cardType;

    @NotBlank(message = "Card number is required")
    // Use a range of 13 to 19 digits to support Visa, Mastercard, Amex, etc.
    // Also allows users to accidentally type spaces (which we will strip in the service), 
    // but strictly for validation, pure digits are safer or use a lenient regex.
    @Pattern(regexp = "^[0-9]{13,19}$", message = "Card number must be between 13 and 19 digits")
    private String cardNumber; 

    @NotBlank(message = "Expiry date is required")
    @Pattern(regexp = "^(0[1-9]|1[0-2])/([0-9]{2})$", message = "Expiry date must be in MM/YY format")
    private String expiryDate;

    @NotBlank(message = "CVV is required")
    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 digits")
    private String cvv; // Needed for verification, never stored

    @NotBlank(message = "Card holder name is required")
    // Allows letters, spaces, hyphens (-), and apostrophes (')
    @Pattern(regexp = "^[a-zA-Z\\s\\-']+$", message = "Card holder name contains invalid characters")
    @Size(min = 2, max = 100, message = "Name length must be between 2 and 100 characters")
    private String cardHolderName;
}