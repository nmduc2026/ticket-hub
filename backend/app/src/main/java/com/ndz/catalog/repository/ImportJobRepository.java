package com.ndz.catalog.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.catalog.entity.ImportJob;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

}
