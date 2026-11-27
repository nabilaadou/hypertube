package com.hypertube.auth.schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

public record LoginRequest(@NotBlank @CodePointLength(max = 120) @Pattern(regexp = "[^\\x00]*") String username,
                           @NotBlank @CodePointLength(max = 128) String password) {}
