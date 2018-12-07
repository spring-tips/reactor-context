package com.example.context;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@SpringBootApplication
@Log4j2
public class MdcApplication {

	public static void main(String arg[]) {
		SpringApplication.run(MdcApplication.class, arg);
	}
}


