package com.ndz.shared.audit;

import org.hibernate.envers.RevisionListener;

import com.ndz.shared.util.CurrentUser;

/** Gắn người đang đăng nhập vào revision; thay đổi không đến từ request đăng nhập (job, listener) để trống. */
public class AuditRevisionListener implements RevisionListener {

	@Override
	public void newRevision(Object revisionEntity) {
		CurrentUser.current().ifPresent(user -> ((AuditRevision) revisionEntity).setActorId(user.id()));
	}

}
