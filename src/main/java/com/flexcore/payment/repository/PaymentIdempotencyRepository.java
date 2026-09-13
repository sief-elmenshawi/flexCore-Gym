package com.flexcore.payment.repository;

import com.flexcore.payment.entity.PaymentIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotency, Long> {

    Optional<PaymentIdempotency> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT INTO payment_idempotency (user_id, idempotency_key, created_date)
            VALUES (:userId, :idempotencyKey, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);

    @Modifying
    @Query("UPDATE PaymentIdempotency p SET p.payment = :payment WHERE p.userId = :userId AND p.idempotencyKey = :idempotencyKey")
    void linkPayment(@Param("userId") Long userId,
                     @Param("idempotencyKey") String idempotencyKey,
                     @Param("payment") com.flexcore.payment.entity.Payment payment);
}