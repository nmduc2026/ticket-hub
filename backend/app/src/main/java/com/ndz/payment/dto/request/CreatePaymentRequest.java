package com.ndz.payment.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreatePaymentRequest")
public record CreatePaymentRequest(@NotNull UUID bookingId) {
}
