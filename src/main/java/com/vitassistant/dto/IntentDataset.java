package com.vitassistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record IntentDataset(@NotEmpty List<@NotNull @Valid IntentDefinition> intents) {
}
