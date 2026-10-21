package com.ndz.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.catalog.entity.PlatformFeePolicy;

/** Lịch sử mức phí nền tảng (BR-06). Mức đang hiệu lực đọc qua {@link CatalogReadRepository#feeBpsAt}. */
public interface PlatformFeePolicyRepository extends JpaRepository<PlatformFeePolicy, UUID> {

	List<PlatformFeePolicy> findAllByOrderByEffectiveFromDescIdDesc();

}
