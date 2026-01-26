package largebeb.dto;

import largebeb.utilities.BillingAddress;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrationRequestDTO {
    @NotBlank(message = "Email is mandatory")
    @Email(message = "Please enter a valid email address")
    private String email;
    @NotBlank(message = "Password is mandatory")
    private String password;
    @NotBlank(message = "Username is mandatory")
    private String username;
    @NotBlank(message = "Role is mandatory")
    private String role;
    @NotBlank(message = "Phone Number is mandatory")
    private String phoneNumber;
    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Birthdate is mandatory")
    private LocalDate birthdate;

    // Manager specific fields
    private String vatNumber;
    private String iban;
    private BillingAddress billingAddress;
}