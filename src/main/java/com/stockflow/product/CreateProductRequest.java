package com.stockflow.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{2,31}",
                message = "must be 3 to 32 characters long and use only A-Z, 0-9 or '-'")
        String sku,

        @NotBlank
        @Size(max = 120)
        String name) {
}
