package com.shashwat.hackcomms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // hourly auto-archive of finished hackathon teams
public class HackcommsApplication {

	public static void main(String[] args) {
		SpringApplication.run(HackcommsApplication.class, args);
	}

}
