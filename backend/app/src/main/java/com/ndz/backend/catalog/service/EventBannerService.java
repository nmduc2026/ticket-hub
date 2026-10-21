package com.ndz.backend.catalog.service;

import java.util.UUID;

import com.ndz.backend.catalog.dto.response.EventResponses.Detail;

/** Upload ảnh banner (jpg / png / webp, ≤ 2 MB). */
public interface EventBannerService {

	Detail upload(UUID organizerId, UUID eventId, byte[] content);

}
