package largebeb.services;

import largebeb.dto.LoginRequestDTO;
import largebeb.dto.LoginResponseDTO;
import largebeb.model.RegisteredUser;
import largebeb.repository.UserRepository;
import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor 
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponseDTO authenticate(LoginRequestDTO request) {
        
        // Find the user in the database by email
        RegisteredUser user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        // Verify that the password matches the hash in the database
        // We check if user is null or password doesn't match
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        // Generate the jwt token for the user session
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getUsername());

        // Create and return the response data transfer object
        return new LoginResponseDTO(token, user.getUsername());
    }
}