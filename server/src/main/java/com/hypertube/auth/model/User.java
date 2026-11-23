package com.hypertube.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String provider;

    @Column(name = "provider_user_id", length = 128)
    private String providerUserId;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(length = 254)
    private String email;

    protected User() {}
}
