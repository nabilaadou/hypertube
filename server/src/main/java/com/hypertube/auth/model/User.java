package com.hypertube.auth.model;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "users_provider_provider_user_id_key",
                columnNames = {"provider", "provider_user_id"}),
        check = @CheckConstraint(name = "users_provider_check", constraint = "provider in ('42', 'google')"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 128)
    private String providerUserId;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(length = 254)
    private String email;

    protected User() {}
}
