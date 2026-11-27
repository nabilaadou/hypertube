package com.hypertube.auth.schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

public record RegistrationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String username,
        @NotBlank @CodePointLength(max = 128) @Pattern(regexp = "[^\\x00]*") String firstName,
        @NotBlank @CodePointLength(max = 128) @Pattern(regexp = "[^\\x00]*") String lastName,
        @NotBlank @CodePointLength(min = 12, max = 128) String password) {}
