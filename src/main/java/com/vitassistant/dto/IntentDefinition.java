package com.vitassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record IntentDefinition(
        @NotBlank String intent,
        @NotBlank String category,
        @NotEmpty List<@NotBlank String> questions
) {
}
