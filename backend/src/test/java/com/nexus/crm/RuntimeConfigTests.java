package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;

import com.nexus.crm.support.IntegrationTest;

/** The runtime settings the app relies on: virtual threads for work, and the size of the database pool. */
class RuntimeConfigTests extends IntegrationTest {

	@Autowired
	private AsyncTaskExecutor applicationTaskExecutor;

	@Autowired
	private TaskScheduler taskScheduler;

	@Autowired
	private DataSource dataSource;

	@Test
	void applicationTasksRunOnVirtualThreads() throws Exception {
		CompletableFuture<Boolean> virtual = CompletableFuture.supplyAsync(() -> Thread.currentThread().isVirtual(),
				this.applicationTaskExecutor);

		assertThat(virtual.get(5, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	void scheduledJobsRunOnVirtualThreads() throws Exception {
		CompletableFuture<Boolean> virtual = new CompletableFuture<>();
		this.taskScheduler.schedule(() -> virtual.complete(Thread.currentThread().isVirtual()), java.time.Instant.now());

		assertThat(virtual.get(5, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	void theDatabasePoolHasThreeToTwentyConnections() throws Exception {
		HikariDataSource pool = this.dataSource.unwrap(HikariDataSource.class);

		assertThat(pool.getMinimumIdle()).isEqualTo(3);
		assertThat(pool.getMaximumPoolSize()).isEqualTo(20);
	}

}
