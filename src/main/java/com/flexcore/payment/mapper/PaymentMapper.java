package com.flexcore.payment.mapper;

import com.flexcore.payment.dto.response.PaymentResponse;
import com.flexcore.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    PaymentResponse toResponse(Payment payment);
}
