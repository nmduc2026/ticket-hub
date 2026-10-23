package com.ndz.booking.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateBookingRequest")
public record CreateBookingRequest(@NotNull UUID eventId, @NotEmpty List<@NotNull UUID> seatIds) {
}
