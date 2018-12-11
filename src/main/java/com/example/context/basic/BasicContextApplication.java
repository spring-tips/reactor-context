package com.example.context.basic;

import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
	* See <a href="https://github.com/spring-projects/spring-security/blob/master/web/src/main/java/org/springframework/security/web/server/context/ReactorContextWebFilter.java">
	*  the implementation in Spring Security</a>.
	*
	*  
	*/
@Log4j2
@SpringBootApplication
public class BasicContextApplication {

	private static final String UID = "uid";

	private static final Scheduler SCHEDULER = Schedulers.fromExecutor(Executors.newFixedThreadPool(10));

	public static void main(String[] args) {
		SpringApplication.run(BasicContextApplication.class, args);
	}

	private <T> Flux<T> context(Flux<T> tPublisher) {
		return this.schedule(tPublisher)
			.doOnEach(stringSignal -> {

				if (!stringSignal.isOnNext()) return;

				Context context = stringSignal.getContext();
				Object uid = context.get(UID);
				log.info("UID: " + uid + " on thread " + Thread.currentThread().getName());
			})
			.subscriberContext(Context.of(UID, UUID.randomUUID()));
	}

	private static class ThreadLogger<T> implements Consumer<T> {

		@Override
		public void accept(T i) {
			log.info(i + ":" + Thread.currentThread().getName());
		}
	}

	@Bean
	RouterFunction<ServerResponse> routes() {
		return route(GET("/data"), req -> ok().body(flux(), String.class));
	}

	@EventListener(ApplicationReadyEvent.class)
	public void go() {
		this.flux().subscribe(log::info);
	}

	private Flux<String> flux() {
		Flux<String> letters = schedule(Flux.just("A", "B", "C"));
		Flux<Integer> numbers = schedule(Flux.just(1, 2, 3));
		Flux<String> map = schedule(
			Flux
				.zip(letters, numbers)
				.map(tpl -> tpl.getT1() + ':' + tpl.getT2())
		);

		return context(map);
	}

	private <T> Flux<T> schedule(Publisher<T> x) {
		Flux<T> result = (x instanceof Flux) ? (Flux<T>) x : Flux.from(x);
		return result.subscribeOn(SCHEDULER).doOnNext(new ThreadLogger<>());
	}

}

