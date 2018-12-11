package com.example.context.mdc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.util.context.Context;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
	* I, Josh Long, took this example DIRECTLY from  <a href="https://simonbasle.github.io/2018/02/contextual-logging-with-reactor-context-and-mdc/">
	*  Simon Baslé's blog on propagating MDC information in the Reactor context
	*  </a>
	*
	* @author Simon Baslé
	* @author Josh Long
	*/
@Data
@AllArgsConstructor
@NoArgsConstructor
class Restaurant {
	private String name;
	private double pricePerPerson;
}

@Log4j2
@Service
class RestaurantService {

	private final Comparator<Restaurant> restaurantComparator = (o1, o2) -> {
		Double a = o1.getPricePerPerson();
		Double b = o2.getPricePerPerson();
		return a.compareTo(b);
	};

	private final Collection<Restaurant> restaurants =
		new ConcurrentSkipListSet<>(this.restaurantComparator);

	private double randomPrice() {
		return ((double) 50 * new Random().nextDouble());
	}

	RestaurantService() {

		char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();

		Stream
			.generate(() -> Character.toString(chars[new Random().nextInt(chars.length)]).toUpperCase())
			.map(name -> new Restaurant(name, randomPrice()))
			.limit(1000)
			.forEach(this.restaurants::add);

		this.restaurants.forEach(log::info);
	}

	Flux<Restaurant> byMaxPrice(double maxPrice) {

		Stream<Restaurant> byMaxPrice = this.restaurants
			.parallelStream()
			.filter(restaurant -> restaurant.getPricePerPerson() <= maxPrice);

		return Flux.fromStream(byMaxPrice);
	}
}

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@SpringBootApplication
@Log4j2
@RestController
public class MdcApplication {

	private final RestaurantService restaurantService;

	public MdcApplication(RestaurantService restaurantService) {
		this.restaurantService = restaurantService;
	}

	@GetMapping("/{userId}/prices/max")
	Flux<Restaurant> byPrice(
		@PathVariable String userId,
		@RequestParam Double maxPrice
	) {
		String apiId = userId == null ? "" : userId;
		return Mono
			.just(String.format("finding restaurants for under $%.2f for %s", maxPrice, apiId))
			.doOnEach(logOnNext(log::info))
			.thenMany(restaurantService.byMaxPrice(maxPrice))
			.doOnEach(logOnNext((Restaurant r) -> log.info("found restaurant {} for ${}", r.getName(), r.getPricePerPerson())))
			.subscriberContext(Context.of("apiID", apiId));
	}


	private static <T> Consumer<Signal<T>> logOnNext(Consumer<T> logStatement) {
		return signal -> {

			if (!signal.isOnNext()) return;

			Optional<String> apiIDMaybe = signal.getContext().getOrEmpty("apiID");


			Runnable orElse = () -> logStatement.accept(signal.get());

			Consumer<String> ifPresent = apiID -> {
				try (MDC.MDCCloseable ignored = MDC.putCloseable("apiID", apiID)) {
					orElse.run();
				}
			};

			apiIDMaybe.ifPresentOrElse(ifPresent, orElse);
		};
	}


	public static void main(String arg[]) {
		SpringApplication.run(MdcApplication.class, arg);
	}
}


