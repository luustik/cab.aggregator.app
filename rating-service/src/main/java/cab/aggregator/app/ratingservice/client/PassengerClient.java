package cab.aggregator.app.ratingservice.client;

import cab.aggregator.app.ratingservice.dto.client.PassengerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "passenger")
public interface PassengerClient {

    @GetMapping("/{id}")
    PassengerResponse getPassengerById(@PathVariable int id);
}
