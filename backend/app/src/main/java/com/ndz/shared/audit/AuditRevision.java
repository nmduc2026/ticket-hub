package com.ndz.shared.audit;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/** Một revision của Envers kèm người thao tác (overview.md §13.4); mọi bảng {@code *_aud} trỏ về đây. */
@Entity
@Table(schema = "shared", name = "revinfo")
@RevisionEntity(AuditRevisionListener.class)
public class AuditRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq")
	@SequenceGenerator(name = "revinfo_seq", sequenceName = "revinfo_seq", allocationSize = 50)
	@RevisionNumber
	private int rev;

	@RevisionTimestamp
	private long revtstmp;

	private UUID actorId;

	public int getRev() {
		return rev;
	}

	public long getRevtstmp() {
		return revtstmp;
	}

	public UUID getActorId() {
		return actorId;
	}

	void setActorId(UUID actorId) {
		this.actorId = actorId;
	}

}
