package largebeb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LargebebApplication {

    public static void main(String[] args) {
        // This command starts Spring Boot
        SpringApplication.run(LargebebApplication.class, args);
    }
}