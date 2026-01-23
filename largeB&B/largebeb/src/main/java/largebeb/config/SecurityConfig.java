package largebeb.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import largebeb.config.JwtAuthenticationFilter; // Assicurati che l'import sia corretto
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // Importante per specificare GET
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disabilita CSRF per le API
            
            .authorizeHttpRequests(auth -> auth
                // 1. ACCESSO LIBERO (Public)
                .requestMatchers(
                    "/api/auth/**",       // Login, Register, Logout
                    "/v3/api-docs/**",    // Swagger Docs
                    "/swagger-ui/**",     // Swagger UI Resources
                    "/swagger-ui.html",   // Swagger UI Page
                    "/error"
                ).permitAll()

                // 2. GUEST & CUSTOMER (Sola Lettura Proprietà)
                // Permetti a CHIUNQUE di fare GET su /api/properties/...
                // Questo sblocca: Ricerca, Dettagli, Mappa, Trending, Raccomandazioni
                .requestMatchers(HttpMethod.GET, "/api/properties/**").permitAll()
                
                // 3. TUTTO IL RESTO RICHIEDE AUTENTICAZIONE
                // (Prenotare, Recensire, Cancellare, ecc.)
                .anyRequest().authenticated()
            )
            
            // Imposta la sessione come Stateless (Fondamentale per JWT)
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())

            // Aggiungi il filtro JWT prima di quello standard
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Configurazione per il pulsante "Authorize" su Swagger
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearer-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-key"));
    }
}