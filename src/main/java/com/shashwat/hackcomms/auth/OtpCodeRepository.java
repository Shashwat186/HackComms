package com.shashwat.hackcomms.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    /** Requesting a new code invalidates any older ones. */
    @Modifying
    @Query("update OtpCode o set o.consumed = true where o.email = :email and o.consumed = false")
    void invalidateAll(String email);
}
