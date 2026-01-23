package largebeb.services;

import largebeb.dto.PaymentMethodRequestDTO;
import largebeb.dto.PaymentMethodResponseDTO;
import largebeb.model.Customer;
import largebeb.model.RegisteredUser;
import largebeb.repository.UserRepository;
import largebeb.utilities.PaymentMethod;
import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    // Regex for basic card validation (13 to 19 digits)
    private static final String CARD_NUMBER_PATTERN = "^[0-9]{13,19}$";
    // Regex for CVV (3 or 4 digits)
    private static final String CVV_PATTERN = "^[0-9]{3,4}$";

    // Retrieve the payment method
    public PaymentMethodResponseDTO getPaymentMethod(String token) {
        Customer user = getCustomerFromToken(token);

        if (user.getPaymentMethod() == null) {
            return null; // Controller will handle this (e.g., 204 No Content)
        }

        return mapToDTO(user.getPaymentMethod());
    }

    public PaymentMethodResponseDTO addPaymentMethod(String token, PaymentMethodRequestDTO request) {
        // Get user entity from token
        Customer user = getCustomerFromToken(token);

        // Validate Expiry Date
        validateExpiryDate(request.getExpiryDate());

        // Validate Card Number Format
        if (request.getCardNumber() == null || !Pattern.matches(CARD_NUMBER_PATTERN, request.getCardNumber().replaceAll("\\s+", ""))) {
            throw new IllegalArgumentException("Invalid card number format.");
        }

        // Validate CVV Format
        if (request.getCvv() == null || !Pattern.matches(CVV_PATTERN, request.getCvv().trim())) {
            throw new IllegalArgumentException("Invalid CVV format.");
        }

        // Create new PaymentMethod object
        PaymentMethod newMethod = new PaymentMethod();
        newMethod.setId(UUID.randomUUID().toString());
        newMethod.setCardType(request.getCardType().toUpperCase());
        newMethod.setCardHolderName(request.getCardHolderName());
        newMethod.setExpiryDate(request.getExpiryDate());
        
        // Store only last 4 digits for security display purposes
        String cleanCardNum = request.getCardNumber().replaceAll("\\s+", "");
        String last4 = cleanCardNum.substring(cleanCardNum.length() - 4);
        newMethod.setLast4Digits(last4);

        // GENERATE GATEWAY TOKEN (Mock Tokenization)
        // In a real scenario, we send the PAN + CVV to Stripe/PayPal, and they return this token.
        // We never store the full number or CVV in our DB (PCI-DSS Compliance).
        String secureGatewayToken = "GTW_TOK_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        newMethod.setGatewayToken(secureGatewayToken);

        // Set the user's payment method
        user.setPaymentMethod(newMethod);
        userRepository.save(user);

        return mapToDTO(newMethod);
    }

    public void deletePaymentMethod(String token) {
        Customer user = getCustomerFromToken(token);
        if (user.getPaymentMethod() == null) {
            throw new IllegalArgumentException("No payment method found for this user.");
        }

        // Set to null to remove payment method
        user.setPaymentMethod(null);
        userRepository.save(user);
    }   

    // Helper Methods

    private void validateExpiryDate(String expiryDate) {
        if (expiryDate == null) throw new IllegalArgumentException("Expiry date is mandatory.");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yy");
        try {
            YearMonth exp = YearMonth.parse(expiryDate, formatter);
            if (exp.isBefore(YearMonth.now())) {
                throw new IllegalArgumentException("The card has expired.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format. Please use MM/yy.");
        }
    }

    private Customer getCustomerFromToken(String token) {
        // Handle Bearer prefix if present
        String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        
        String userId = jwtUtil.getUserIdFromToken(cleanToken);
        
        RegisteredUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        // Ensure this is actually a Customer
        if (!(user instanceof Customer)) {
            throw new SecurityException("Only customers can manage payment methods.");
        }

        return (Customer) user;
    }
     
    private PaymentMethodResponseDTO mapToDTO(PaymentMethod m) {
        return new PaymentMethodResponseDTO(
            m.getId(), 
            m.getCardType(), 
            m.getLast4Digits(), 
            m.getExpiryDate(), 
            m.getCardHolderName()
        );
    }
}