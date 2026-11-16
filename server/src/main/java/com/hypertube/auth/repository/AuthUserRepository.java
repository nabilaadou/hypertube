package com.hypertube.auth.repository;

import java.util.Optional;

import com.hypertube.auth.model.AuthUser;
import com.hypertube.auth.model.UserProfile;
import org.springframework.data.repository.Repository;

public interface AuthUserRepository extends Repository<AuthUser, Long> {
    Optional<UserProfile> findProfileById(long id);
}
