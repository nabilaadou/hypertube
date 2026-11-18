package com.hypertube.auth.repository;

import java.util.Optional;

import com.hypertube.auth.model.User;
import com.hypertube.auth.schema.UserProfile;
import org.springframework.data.repository.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends Repository<User, Long> {
    Optional<UserProfile> findProfileById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findLockedById(@Param("id") long id);
}
