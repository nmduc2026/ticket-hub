package com.ndz.archfixtures.catalog.internal.web;

import com.ndz.archfixtures.catalog.internal.persistence.EventRepository;

/** Fixture cho test kiến trúc: controller gọi thẳng repository. */
public class EventController {

	EventRepository repository = new EventRepository();

}
