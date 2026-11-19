package com.hypertube.auth.schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

public record OAuthIdentity(
        @NotNull @Pattern(regexp = "42|google|github") String provider,
        @NotBlank @CodePointLength(max = 128) @Pattern(regexp = NO_NUL) String providerUserId,
        @CodePointLength(max = 128) @Pattern(regexp = NO_NUL) String displayName,
        @CodePointLength(max = 254) @Pattern(regexp = NO_NUL) String email,
        @NotNull @Pattern(regexp = "[0-9a-f]{64}") String proofHash) {

    private static final String NO_NUL = "[^\\x00]*";
}
