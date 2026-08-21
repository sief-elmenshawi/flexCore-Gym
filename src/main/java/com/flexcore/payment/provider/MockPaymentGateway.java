package com.flexcore.payment.provider;

import com.flexcore.payment.enums.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class MockPaymentGateway {

    public boolean process(java.math.BigDecimal amount, PaymentMethod method) {
        log.info("MockPaymentGateway processing {} EGP via {}", amount, method);
        return ThreadLocalRandom.current().nextInt(100) < 90;
    }
}
