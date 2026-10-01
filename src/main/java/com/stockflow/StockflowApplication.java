package com.stockflow;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "StockFlow API",
        version = "v1",
        description = "Multi-store retail inventory: goods receipts, stock levels, "
                + "an audit ledger of every stock movement and inter-store transfers."))
public class StockflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockflowApplication.class, args);
    }

    /**
     * Single source of "now" for the application. Services ask this clock instead of calling
     * Instant.now() directly, so unit tests can freeze time and assert exact timestamps.
     * <p>
     * It ticks in whole milliseconds, a precision every supported database stores exactly, so a
     * timestamp returned right after a write is identical to the one read back later.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
