package com.hypertube.auth.schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

public record OAuthProfileRequest(@NotBlank @Size(max = 120) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @CodePointLength(max = 128) @Pattern(regexp = "[^\\x00]*") String firstName,
        @NotBlank @CodePointLength(max = 128) @Pattern(regexp = "[^\\x00]*") String lastName) {}
