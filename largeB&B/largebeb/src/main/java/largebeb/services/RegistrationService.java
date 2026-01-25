package largebeb.services;

import largebeb.dto.RegistrationRequestDTO;
import largebeb.dto.RegistrationResponseDTO;
import largebeb.model.Customer;
import largebeb.model.Manager;
import largebeb.model.RegisteredUser;
import largebeb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Password: 8+ chars, 1 uppercase, 1 lowercase, 1 number, 1 special character
    private static final String PASSWORD_PATTERN = 
        "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";

    // Universal IBAN (ISO 13616): 
    // Starts with 2 Country Code letters, 2 Check Digits, followed by 11-30 alphanumeric chars.
    private static final String UNIVERSAL_IBAN_PATTERN = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$";

    // Universal Tax ID / VAT: 
    // Alphanumeric + hyphens, length 5-20.
    private static final String UNIVERSAL_TAX_ID_PATTERN = "^[A-Z0-9\\-]{5,20}$";

    // Universal Phone Number: Optional '+', followed by 7 to 20 digits/spaces/dashes.
    private static final String UNIVERSAL_PHONE_PATTERN = "^\\+?[0-9\\s\\-\\.]{7,20}$";

    public RegistrationResponseDTO register(RegistrationRequestDTO request) {
                
        // Basic Field Validation
        if (isStringEmpty(request.getUsername()) || isStringEmpty(request.getEmail())) {
            return new RegistrationResponseDTO("Username and Email are mandatory.", null, null);
        }

        // Phone Validation
        if (request.getPhoneNumber() == null || !isValidPhoneNumber(request.getPhoneNumber())) {
            return new RegistrationResponseDTO("Invalid Phone Number format.", null, null);
        }

        // Age and Date Format Validation
        // If parsing fails before reaching here, Spring throws a HttpMessageNotReadableException.
        if (request.getBirthdate() == null) {
            return new RegistrationResponseDTO("Birthdate is mandatory and must follow the format 'yyyy-MM-dd'.", null, null);
        }
        
        // Check if user is at least 18 years old
        if (request.getBirthdate().isAfter(LocalDate.now().minusYears(18))) {
             return new RegistrationResponseDTO("User must be at least 18 years old to register.", null, null);
        }

        // Check if Email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return new RegistrationResponseDTO("This email address is already in use.", null, null);
        }

        // Check if Username already exists
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return new RegistrationResponseDTO("This username is already taken.", null, null);
        }

        // Password Validation
        if (!isValidPassword(request.getPassword())) {
            return new RegistrationResponseDTO(
                "Password is too weak. It requires: 8+ chars, one uppercase, one lowercase, one number, and one special character.", 
                null, null);
        }

        RegisteredUser newUser;

        // Role-Specific Logic
        if ("MANAGER".equalsIgnoreCase(request.getRole())) {            
            // Validate VAT
            if (request.getVatNumber() == null || !isValidTaxId(request.getVatNumber())) {
                return new RegistrationResponseDTO("Invalid VAT/Tax ID format.", null, null);
            }

            // Validate IBAN
            if (request.getIban() == null || !isValidIban(request.getIban())) {
                return new RegistrationResponseDTO("Invalid IBAN format.", null, null);
            }

            // Validate Billing Address
            if (request.getBillingAddress() == null ||
                isStringEmpty(request.getBillingAddress().getStreet()) ||
                isStringEmpty(request.getBillingAddress().getCity()) ||
                isStringEmpty(request.getBillingAddress().getZipCode()) ||
                isStringEmpty(request.getBillingAddress().getCountry()) ||
                isStringEmpty(request.getBillingAddress().getStateProvince())) {
                
                return new RegistrationResponseDTO("Managers must provide full billing address details.", null, null);
            }

            Manager manager = new Manager();
            manager.setVatNumber(request.getVatNumber().trim().toUpperCase());
            manager.setIban(request.getIban().replaceAll("\\s+", "").toUpperCase());
            manager.setBillingAddress(request.getBillingAddress());
            
            newUser = manager;

        } else if ("CUSTOMER".equalsIgnoreCase(request.getRole())) {            
            newUser = new Customer();
        } else {
            return new RegistrationResponseDTO("Invalid role specified. Must be 'MANAGER' or 'CUSTOMER'.", null, null);
        }

        // Finalize User Object
        newUser.setEmail(request.getEmail());
        newUser.setUsername(request.getUsername());
        newUser.setRole(request.getRole().toUpperCase());
        newUser.setPhoneNumber(request.getPhoneNumber());
        newUser.setBirthdate(request.getBirthdate());

        // Encrypt Password
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        newUser.setPassword(hashedPassword);

        // Save to DB
        RegisteredUser savedUser = userRepository.save(newUser);
        
        return new RegistrationResponseDTO(
            "Registration successful", 
            savedUser.getId(), 
            savedUser.getRole()
        );
    }

    // Helper Methods

    private boolean isValidPassword(String password) {
        if (password == null) return false;
        return Pattern.compile(PASSWORD_PATTERN).matcher(password).matches();
    }

    private boolean isValidIban(String iban) {
        if (iban == null) return false;
        // Clean spaces/dashes before regex check
        return Pattern.matches(UNIVERSAL_IBAN_PATTERN, iban.replaceAll("[\\s\\-]", "").toUpperCase());
    }

    private boolean isValidTaxId(String vat) {
        if (vat == null) return false;
        return Pattern.matches(UNIVERSAL_TAX_ID_PATTERN, vat.trim().toUpperCase());
    }

    private boolean isValidPhoneNumber(String phone) {
        if (phone == null) return false;
        return Pattern.matches(UNIVERSAL_PHONE_PATTERN, phone.trim());
    }

    private boolean isStringEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}