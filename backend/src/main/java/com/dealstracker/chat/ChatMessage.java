package com.dealstracker.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChatMessage(@Pattern(regexp = "user|assistant") String role, @NotBlank String content) {
}
