package com.hypertube.auth.schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeConfirmation(@NotBlank @Size(max = 512) String token) {}
