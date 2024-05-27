package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Flight;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Seat;
import be.kuleuven.distributedsystems.cloud.entities.User;
import be.kuleuven.distributedsystems.cloud.util.Config;
import be.kuleuven.distributedsystems.cloud.util.LocalDateAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class FlightAPIController {

    private int maxtries = 10;
    private String[] airlines = {"reliable-airline.com","unreliable-airline.com",Config.localStorePseudo};

    @Autowired
    WebClient.Builder webClientBuilder;

    @Autowired
    String projectId;

    @Autowired
    boolean isProduction;

    @PostConstruct
    private void init() {
        airlines = new String[]{"reliable-airline.com","unreliable-airline.com",
                (isProduction ? Config.cloudURL : Config.localStorePseudo)
        };
    }

    @GetMapping("/api/getFlights")
    public String getFlights( ) {

        ArrayList<Flight> flights = new ArrayList<>();
        for(String airline: airlines) {
            flights.addAll(getFlightsWeb( (airline.equals(Config.localStorePseudo) ? Config.localStoreUrl : "https://" + airline) ));
        }
        return new Gson().toJson(flights);
    }

    @GetMapping("/api/getFlight")
    public String getFlight(@RequestParam String flightId, @RequestParam String airline) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateAdapter())
                .create();

        return gson.toJson(getFlightWeb((airline.equals(Config.localStorePseudo) ? Config.localStoreUrl : "https://" + airline), flightId));
    }

    @GetMapping("/api/getFlightTimes")
    public String getFlighttimes(@RequestParam String flightId, @RequestParam String airline) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateAdapter())
                .create();

        return gson.toJson(getFlightTimesWeb((airline.equals(Config.localStorePseudo) ? Config.localStoreUrl : "https://" + airline), flightId));
    }

    @GetMapping("/api/getAvailableSeats")
    public String getAvailableSeats(@RequestParam String flightId, @RequestParam String airline, @RequestParam String time) {
        Collection<Seat> seats = getAvailableSeatsWeb((airline.equals(Config.localStorePseudo) ? Config.localStoreUrl : "https://" + airline), flightId, time);

        HashMap<String,ArrayList<Seat>> response = new HashMap<>();

        for (Seat seat: seats ) {
            if(!response.containsKey(seat.getType()))
                response.put(seat.getType(),new ArrayList<>());

            response.get(seat.getType()).add(seat);
        }

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateAdapter())
                .create();

        return gson.toJson(response);
    }

    @GetMapping("/api/getSeat")
    public String getFlight(@RequestParam String flightId, @RequestParam String seatId ,@RequestParam String airline) {

        Seat seat = getSeatWeb((airline.equals(Config.localStorePseudo) ? Config.localStoreUrl : "https://" + airline), flightId, seatId);

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateAdapter())
                .create();

        return gson.toJson(seat);


    }

    @PostMapping("/api/confirmQuotes")
    public void confirmQuotes(@RequestBody Quote[] quotes) throws IOException {

        String customer = ( (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .getEmail();

        ManagedChannel channel = null;
        Publisher publisher = null;

        try {
            TopicName topicName = TopicName.of(projectId, "booking");

            if(!isProduction) {
                String hostport = "localhost:8083";
                channel = ManagedChannelBuilder.forTarget(hostport).usePlaintext().build();
                TransportChannelProvider channelProvider =
                        FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
                CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

                publisher =
                        Publisher.newBuilder(topicName)
                                .setChannelProvider(channelProvider)
                                .setCredentialsProvider(credentialsProvider)
                                .build();
            } else {
                publisher = Publisher.newBuilder(topicName)
                        .build();
            }


            String data = Base64.getEncoder().encodeToString(
                    new ObjectMapper().writeValueAsString(quotes).getBytes());

            var emptyBytes = ByteString.copyFromUtf8("");

            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(emptyBytes)
                    .putAttributes("customer", customer)
                    .putAttributes("data", data).build();
            ApiFuture<String> future = publisher.publish(pubsubMessage);
            future.get();


        } catch (Exception e) {

        } finally {
            if(channel != null)
                channel.shutdown();
            if(publisher != null)
                publisher.shutdown();
        }

    }

    private Collection<Flight> getFlightsWeb(String airline) {
        for (int i = 0; i< maxtries; i++) {
            try {
                Collection<Flight> flights = webClientBuilder
                        .baseUrl(airline)
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("flights")
                                .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                .build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<CollectionModel<Flight>>() {})
                        .block()
                        .getContent();

                return flights;
            } catch (Exception e) {}
        }

        return new ArrayList<>();
    }

    private Flight getFlightWeb(String company, String id) {
        for (int i = 0; i< maxtries; i++) {
            try {
                Flight flight = webClientBuilder
                        .baseUrl(company)
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("flights")
                                .pathSegment(id)
                                .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                .build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Flight>() {
                        })
                        .block();

                return flight;
            } catch (Exception e) {}
        }

        return null;
    }

    private Collection<String> getFlightTimesWeb(String company, String id) {
        for (int i = 0; i< maxtries; i++) {
            try {
                Collection<String> times = webClientBuilder
                        .baseUrl(company)
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("flights")
                                .pathSegment(id)
                                .pathSegment("times")
                                .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                .build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<CollectionModel<String>>() {})
                        .block()
                        .getContent();

                return times;
            } catch (Exception e) {}
        }

        return null;
    }

    private Collection<Seat> getAvailableSeatsWeb(String company, String id, String time) {
        for (int i = 0; i< maxtries; i++) {
            try {
                Collection<Seat> seats = webClientBuilder
                        .baseUrl(company)
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("flights")
                                .pathSegment(id)
                                .pathSegment("seats")
                                .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                .queryParam("time", time)
                                .queryParam("available", "true")
                                .build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<CollectionModel<Seat>>() {})
                        .block()
                        .getContent();

                return seats;
            } catch (Exception e) {}
        }

        return null;
    }

    private Seat getSeatWeb(String company, String flightId, String seatId) {
        for (int i = 0; i< maxtries; i++) {
            try {
                Seat seat = webClientBuilder
                        .baseUrl(company)
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("flights")
                                .pathSegment(flightId)
                                .pathSegment("seats")
                                .pathSegment(seatId)
                                .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                .build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Seat>() {})
                        .block();

                return seat;
            } catch (Exception e) {}
        }

        return null;
    }

    public Map<String, Object> serialize(Object object) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.convertValue(object, Map.class);
    }
}
