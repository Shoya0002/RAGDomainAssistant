package com.vitassistant.dto;

import java.util.List;

public record ChatResponse(String answer, List<String> sources, String intent) {
}
