package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.*;
import be.kuleuven.distributedsystems.cloud.entities.fireBase.FireBaseBooking;
import be.kuleuven.distributedsystems.cloud.util.Config;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;

@RestController
public class PubSubController {

    @Autowired
    String projectId;

    @Autowired
    boolean isProduction;

    @Autowired
    WebClient.Builder webClientBuilder;

    private int maxtries = 10;

    @PostMapping("/booking")
    public void subscription(@RequestBody String body) {
        ArrayList<Quote> quotes;
        String customer;

        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(body);
            var message = json.getAsString("message");
            var jsonmessage = (JSONObject) parser.parse(message);
            var attributes = jsonmessage.getAsString("attributes");
            var jsonattributes = (JSONObject) parser.parse(attributes);
            customer = jsonattributes.getAsString("customer");
            var realdata = jsonattributes.getAsString("data");
            byte[] decodedBytes = Base64.getDecoder().decode(realdata);
            var stringbytes = new String(decodedBytes);
            quotes = new ArrayList<Quote>(new ObjectMapper().readValue(stringbytes, new TypeReference<>() {
            }));
        } catch (Exception e) {
            return;
        }

        ArrayList<Ticket> tickets = new ArrayList<>();
        Ticket ticket;

        UUID bookingId = UUID.randomUUID();

        for (Quote quote: quotes) {
            String airline = quote.getAirline();
            ticket = PutTicketWeb((airline.equals(Config.localStorePseudo) ? Config.localStoreUrl : "https://" + airline),
                    quote.getFlightId().toString(), quote.getSeatId().toString(), customer, bookingId.toString());


            if(ticket == null){
                for (Ticket bookedTicket: tickets) {
                    DeleteTicketWeb((airline.equals(Config.localStorePseudo) ? Config.localStoreUrl : "https://" + airline),
                            quote.getFlightId().toString(), quote.getSeatId().toString(), ticket.getTicketId().toString());
                }
                sendMailFailed(customer);
                return;
            }

            tickets.add(ticket);
        }

        Booking booking = new Booking(bookingId, LocalDateTime.now(), tickets, customer);

        FirestoreOptions firestoreOptions = null;

        try {
            if (isProduction) {
                firestoreOptions =
                        FirestoreOptions.getDefaultInstance().toBuilder()
                                .setProjectId(projectId)
                                .setCredentials(GoogleCredentials.getApplicationDefault())
                                .build();
            } else {
                firestoreOptions =
                        FirestoreOptions.newBuilder()
                                .setEmulatorHost("localhost:8084")
                                .setProjectId(projectId)
                                .build();
            }
        } catch (Exception e) {}

        Firestore db = firestoreOptions.getService();
        db.collection("bookings")
                .document("customers")
                .collection(customer)
                .document(booking.getId().toString())
                .set(FireBaseBooking.fromBooking(booking));
        db.shutdown();

        sendMail(booking, customer);
    }

    private Ticket PutTicketWeb(String company, String flightId, String seatId, String customer, String bookingReference) {
        for (int i = 0; i< maxtries; i++) {
            try {
                Ticket ticket = webClientBuilder
                        .baseUrl(company)
                        .build()
                        .put()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("flights")
                                .pathSegment(flightId)
                                .pathSegment("seats")
                                .pathSegment(seatId)
                                .pathSegment("ticket")
                                .queryParam("customer", customer)
                                .queryParam("bookingReference", bookingReference)
                                .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                .build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Ticket>() {
                        })
                        .block();

                return ticket;
            } catch (Exception e) {
            }
        }

        return null;
    }

    private void DeleteTicketWeb(String company, String flightId, String seatId, String ticketId) {
        for (int i = 0; i< maxtries; i++) {
            try {
                webClientBuilder
                        .baseUrl(company)
                        .build()
                        .put()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("flights")
                                .pathSegment(flightId)
                                .pathSegment("seats")
                                .pathSegment(seatId)
                                .pathSegment("ticket")
                                .pathSegment(ticketId)
                                .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                .build())
                        .retrieve();
                return;
            } catch (Exception e) {
            }
        }

        return;
    }

    private void  sendMail(Booking booking, String email) {
        Email from = new Email("tim.vanmeerbeeck@student.kuleuven.be");
        String subject = "your booking has been successful";
        Email to = new Email(email);

        String contentString = booking.getId() + "\n";
        contentString += "The following Seats have been booked: \n \n";
        double totalPrice = 0;

        for (Ticket ticket: booking.getTickets()) {
            Seat seat = getSeatWeb((ticket.getAirline().equals(Config.localStorePseudo) ? Config.localStoreUrl : "https://" + ticket.getAirline()),
                    ticket.getFlightId().toString(), ticket.getSeatId().toString());
            Flight flight = getFlightWeb((ticket.getAirline().equals(Config.localStorePseudo) ? Config.localStoreUrl : "https://" + ticket.getAirline()), ticket.getFlightId().toString());
            contentString += ticket.getTicketId() + ":\n";
            contentString += flight.getAirline() + ":" + flight.getLocation() + " " + seat.getTime() + "\n";
            contentString += seat.getName() + "\n";
            contentString += "cost: €" + seat.getPrice() + "\n";
            contentString += "\n";
            totalPrice += seat.getPrice();
        }

        contentString += "total price €: " + totalPrice;

        Content content = new Content("text/plain", contentString);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(Config.sendGridAPIKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            System.out.println("message");
            System.out.println(response.getStatusCode());
            System.out.println(response.getBody());
            System.out.println(response.getHeaders());
        } catch (IOException e) {
            System.out.equals("error:");
            System.out.equals(e);
        }
    }

    private void  sendMailFailed(String email) {
        Email from = new Email("tim.vanmeerbeeck@student.kuleuven.be");
        String subject = "your booking has failed";
        Email to = new Email(email);

        String contentString = "unfortunately we were not able to finilize your booking.\n" +
                "One of the seats has most likely been booked while you were browsing the application. \n" +
                "We have thus cancelled your booking. \n" +
                "We apologise for this inconvenience";

        Content content = new Content("text/plain", contentString);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(Config.sendGridAPIKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            System.out.println("message");
            System.out.println(response.getStatusCode());
            System.out.println(response.getBody());
            System.out.println(response.getHeaders());
        } catch (IOException e) {
            System.out.equals("error:");
            System.out.equals(e);
        }
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

}
