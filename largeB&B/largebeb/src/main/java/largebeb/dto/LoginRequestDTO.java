package largebeb.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor 
public class LoginRequestDTO {
    @NotBlank(message = "Email is mandatory")
    @Email(message = "Please enter a valid email address")
    private String email;
    @NotBlank(message = "Password is mandatory")
    private String password;
}