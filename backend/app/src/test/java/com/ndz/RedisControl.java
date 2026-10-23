package com.ndz;

import org.testcontainers.containers.GenericContainer;

/**
 * Điều khiển container Redis của test. {@code pause} làm mọi kết nối treo (giống Redis chết hoặc mất mạng) nhưng giữ
 * nguyên cổng, nên {@code unpause} xong ứng dụng kết nối lại được mà không cần đổi cấu hình.
 */
public class RedisControl {

	private final GenericContainer<?> container;

	RedisControl(GenericContainer<?> container) {
		this.container = container;
	}

	public void pause() {
		container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
	}

	public void resume() {
		container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
	}

}
