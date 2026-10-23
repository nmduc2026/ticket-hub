package com.ndz.archfixtures.catalog.controller;

import com.ndz.archfixtures.catalog.repository.EventRepository;

/** Fixture cho test kiến trúc: controller gọi thẳng repository. */
public class EventController {

	EventRepository repository = new EventRepository();

}
