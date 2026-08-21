package com.flexcore.payment.repository;

import com.flexcore.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    interface MethodTotal {
        String getMethod();

        BigDecimal getTotal();

        long getCount();
    }

    @Query("""
            SELECT p.method AS method, SUM(p.amount) AS total, COUNT(p) AS count
            FROM Payment p
            WHERE p.status = com.flexcore.payment.enums.PaymentStatus.SUCCESS
              AND p.paidAt BETWEEN :start AND :end
            GROUP BY p.method
            """)
    List<MethodTotal> aggregateTotalsByMethodBetween(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);
}
