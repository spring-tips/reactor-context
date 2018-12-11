package com.example.reactorcontext.simple;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@Log4j2
@RestController
@SpringBootApplication
public class SimpleApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimpleApplication.class, args);
	}


	private static Scheduler SCHEDULER = Schedulers.fromExecutor(Executors.newFixedThreadPool(10));

	private static <T> Flux<T> prepare(Flux<T> in) {
		return in
			.doOnNext(log::info)
			.subscribeOn(SCHEDULER);
	}

	Flux<String> read() {
		Flux<String> letters = prepare(Flux.just("A", "B", "C"));
		Flux<Integer> numbers = prepare(Flux.just(1, 2, 3));
		return prepare(Flux.zip(letters, numbers).map(tuple -> tuple.getT1() + ':' + tuple.getT2()))
			.doOnEach(signal -> {
				if (!signal.isOnNext()) {
					return;
				}

				Context context = signal.getContext();
				Object userId = context.get("userId");
				log.info("user id for this pipeline stage for data '" + signal.get() + "'  is '" + userId + "'");
			})
			.subscriberContext(Context.of("userId", UUID.randomUUID().toString()));
	}

	@GetMapping("/data")
	Flux<String> get() {
		return read();
	}



}
