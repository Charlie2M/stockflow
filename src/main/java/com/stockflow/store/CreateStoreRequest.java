package com.stockflow.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStoreRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{2,15}",
                message = "must be 3 to 16 characters long and use only A-Z, 0-9 or '-'")
        String code,

        @NotBlank
        @Size(max = 120)
        String name) {
}
