package com.ndz.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ndz.catalog.entity.AdministrativeUnit;

public interface AdministrativeUnitRepository extends JpaRepository<AdministrativeUnit, UUID> {

	Optional<AdministrativeUnit> findByCode(String code);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, UUID id);

	List<AdministrativeUnit> findByLevelOrderByName(short level);

	List<AdministrativeUnit> findByParentIdOrderByName(UUID parentId);

	/** Còn đơn vị con đang hoạt động (chặn tắt cấp 1). */
	boolean existsByParentIdAndActiveTrue(UUID parentId);

	/** Khoá dòng để hai Admin cùng sửa / tắt một đơn vị không chồng lên nhau. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from AdministrativeUnit u where u.id = :id")
	Optional<AdministrativeUnit> lockById(@Param("id") UUID id);

}
