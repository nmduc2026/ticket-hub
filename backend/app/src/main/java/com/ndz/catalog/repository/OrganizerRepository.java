package com.ndz.catalog.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ndz.catalog.entity.Organizer;
import com.ndz.catalog.entity.enums.OrganizerStatus;

public interface OrganizerRepository extends JpaRepository<Organizer, UUID> {

	/** Khoá dòng để hai Admin (hoặc nộp và duyệt) đồng thời không cùng chuyển một hồ sơ. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Organizer o where o.id = :id")
	Optional<Organizer> lockById(@Param("id") UUID id);

	/** Hồ sơ đã nộp (có {@code submitted_at}), lọc theo trạng thái nếu có, mới nộp trước. */
	@Query(value = """
			select o from Organizer o
			where o.submittedAt is not null and (:status is null or o.status = :status)
			order by o.submittedAt desc, o.id
			""", countQuery = """
			select count(o) from Organizer o
			where o.submittedAt is not null and (:status is null or o.status = :status)
			""")
	Page<Organizer> findApplications(@Param("status") OrganizerStatus status, Pageable pageable);

	/** Hồ sơ chờ duyệt, cho Admin dashboard (§11.2). */
	long countByStatusAndSubmittedAtNotNull(OrganizerStatus status);

	long countByStatusIn(Collection<OrganizerStatus> statuses);

}
