package com.hypertube.auth.schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

public record ProfileUpdate(
        @Size(max = 120) String username,
        @CodePointLength(max = 128) @Pattern(regexp = "(?s)(?=.*\\S)[^\\x00]*") String firstName,
        @CodePointLength(max = 128) @Pattern(regexp = "(?s)(?=.*\\S)[^\\x00]*") String lastName,
        @Email @Size(max = 254) @Pattern(regexp = "(?s).*\\S.*") String email,
        @CodePointLength(max = 128) String password) {}
