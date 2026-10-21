/**
 * Dùng chung: ProblemDetail, BusinessException, idempotency, tiện ích tiền và thời gian. Không chứa logic nghiệp vụ của module.
 */
@ApplicationModule(displayName = "Shared", type = ApplicationModule.Type.OPEN)
package com.ndz.shared;

import org.springframework.modulith.ApplicationModule;
