package com.ndz.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ndz.notification.entity.Template;
import com.ndz.notification.entity.Template.TemplateId;

public interface TemplateRepository extends JpaRepository<Template, TemplateId> {
}
