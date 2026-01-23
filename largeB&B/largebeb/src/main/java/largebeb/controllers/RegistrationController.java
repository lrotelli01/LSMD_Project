package largebeb.controllers;

import largebeb.dto.RegistrationRequestDTO;
import largebeb.dto.RegistrationResponseDTO;
import largebeb.services.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponseDTO> register(@Valid @RequestBody RegistrationRequestDTO request) {
        RegistrationResponseDTO response = registrationService.register(request);
        return response.getId() != null ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }
}