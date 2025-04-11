package com.example.demo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.GoogleDriveApplication;

@SpringBootTest(classes = GoogleDriveApplication.class)
class DemoApplicationTests {

	@Test
	@Disabled("Disabled until OAuth configuration is fully set up")
	void contextLoads() {
	}

}
