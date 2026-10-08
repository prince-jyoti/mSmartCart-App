package com.example.user_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Starting the full context needs PostgreSQL, RabbitMQ and Eureka, so this only works
// inside the docker compose stack. The unit tests next to it run anywhere.
@Disabled("Needs the running stack (database, broker, Eureka)")
@SpringBootTest
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
