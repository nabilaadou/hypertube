package com.hypertube.auth.repository;

import java.util.Optional;

import com.hypertube.auth.model.User;
import com.hypertube.auth.schema.UserProfile;
import org.springframework.data.repository.Repository;

public interface UserRepository extends Repository<User, Long> {
    Optional<UserProfile> findProfileById(long id);
}
