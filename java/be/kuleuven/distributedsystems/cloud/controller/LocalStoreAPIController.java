package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Flight;
import be.kuleuven.distributedsystems.cloud.entities.Seat;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import be.kuleuven.distributedsystems.cloud.entities.fireBase.FireBaseFlight;
import be.kuleuven.distributedsystems.cloud.entities.fireBase.FireBaseSeat;
import be.kuleuven.distributedsystems.cloud.entities.fireBase.FireBaseTicket;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
//@RequestMapping("/store")
public class LocalStoreAPIController {

    @Autowired
    String projectId;

    @Autowired
    boolean isProduction;

    private Firestore getFireStore() {
        //ApplicationContext context = new AnnotationConfigApplicationContext(Application.class);
        //String projectId = context.getBean("projectId", String.class);
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
        } catch (Exception e) {};

        return firestoreOptions.getService();
    }

    @GetMapping(path = "/flights")
    public CollectionModel<Flight> getFlights() {
        Firestore db = getFireStore();

        ApiFuture<QuerySnapshot> future = db.collection("flights").get();

        ArrayList<Flight> flights = new ArrayList<>();

        try {
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (DocumentSnapshot document : documents) {
                flights.add(
                        document.toObject(FireBaseFlight.class).toFlight()
                );
            }
        } catch (Exception e) {
            System.err.println(e);
            db.shutdown();
            return null;
        }

        CollectionModel<Flight> flightsEntity = CollectionModel.of(flights);
        db.shutdown();
        return flightsEntity;
    }

    @GetMapping(path = "/flights/{flightId}")
    public EntityModel<Flight> getFlight(@PathVariable String flightId) {
        Firestore db = getFireStore();

        ApiFuture<DocumentSnapshot> future = db.collection("flights").document(flightId).get();

        Flight flight;

        try {
            flight = future.get().toObject(FireBaseFlight.class).toFlight();
        } catch (Exception e) {
            db.shutdown();
            return null;
        }

        db.shutdown();

        return EntityModel.of(flight);
    }

    @GetMapping(path = "/flights/{flightId}/times")
    public CollectionModel<LocalDateTime> getTimes(@PathVariable String flightId) {
        Firestore db = getFireStore();
        ArrayList<LocalDateTime> times = new ArrayList<>();

        try {
            Iterable<CollectionReference> collections = db.collection("seats").document(flightId).listCollections();
            for (CollectionReference collection: collections) {
                times.add(LocalDateTime.parse(collection.getId()));
            }
        } catch (Exception e) {
            db.shutdown();
            return null;
        }

        CollectionModel<LocalDateTime> timesEntity = CollectionModel.of(times);

        db.shutdown();

        return timesEntity;
    }

    @GetMapping(path = "/flights/{flightId}/seats")
    public CollectionModel<Seat> getSeats(@PathVariable String flightId, @RequestParam String time
            , @RequestParam boolean available) {
        Firestore db = getFireStore();
        ArrayList<Seat> seats = new ArrayList<>();

        //TODO: temp fix
        time = time.replace(":00","");

        try {
            ApiFuture<QuerySnapshot> future = db.collection("seats").document(flightId).
                    collection(time).whereEqualTo("available",available).get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();

            for (DocumentSnapshot document : documents) {
                seats.add(document.toObject(FireBaseSeat.class).toSeat());
            }
        } catch (Exception e) {
            System.err.println(e);
            db.shutdown();
            return null;
        }

        CollectionModel<Seat> seatsEntity = CollectionModel.of(seats);

        db.shutdown();

        return seatsEntity;
    }

    @GetMapping(path = "/flights/{flightId}/seats/{seatId}")
    public EntityModel<Seat> getSeats(@PathVariable String flightId, @PathVariable String seatId) {
        Firestore db = getFireStore();

        Iterable<CollectionReference> collections =
                db.collection("seats").document(flightId).listCollections();

        Seat seat = null;
        try {
            for (CollectionReference collection : collections) {
                ApiFuture<DocumentSnapshot> future = collection.document(seatId).get();
                DocumentSnapshot document = future.get();
                if(!document.exists())
                    continue;
                seat = document.toObject(FireBaseSeat.class).toSeat();
                break;
            }
        } catch (Exception e) {
            System.out.println(e);
            db.shutdown();
            return null;
        }

        db.shutdown();

        return EntityModel.of(seat);
    }



    @PutMapping(path = "/flights/{flightId}/seats/{seatId}/ticket")
    public EntityModel<Ticket> putTicket(@PathVariable String flightId, @PathVariable String seatId,
                                         @RequestParam String customer, @RequestParam String bookingReference) throws ExecutionException, InterruptedException {

        Firestore db = getFireStore();

        Iterable<CollectionReference> collections =
                db.collection("seats").document(flightId).listCollections();

        DocumentSnapshot document = null;
        try {
            for (CollectionReference collection : collections) {
                ApiFuture<DocumentSnapshot> future = collection.document(seatId).get();
                document = future.get();
                if(!document.exists())
                    continue;
                break;
            }
        } catch (Exception e) {
            System.out.println(e);
            db.shutdown();
            return null;
        }

        DocumentReference ref = document.getReference();

        ApiFuture<FireBaseSeat> futureTransaction =
                db.runTransaction(
                        transaction -> {
                            // retrieve document and increment population field
                            DocumentSnapshot snapshot = transaction.get(ref).get();
                            FireBaseSeat seat = snapshot.toObject(FireBaseSeat.class);
                            if(!seat.isAvailable())
                                return null;
                            transaction.update(ref, "available", false);
                            return seat;
                        });

        FireBaseSeat seat = futureTransaction.get();

        //String company, UUID flightId, UUID seatId, UUID ticketId, String customer
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket( seat.getAirline(),
                UUID.fromString(seat.getFlightId()),
                UUID.fromString(seat.getSeatId()),
                ticketId,
                customer,
                bookingReference
        );

        db.collection("tickets").document(ticketId.toString()).set(FireBaseTicket.fromTicket(ticket));

        db.shutdown();

        return EntityModel.of(ticket);
    }

    @GetMapping(path = "/flights/{flightId}/seats/{seatId}/ticket")
    public EntityModel<Ticket> getTicket(@PathVariable String flightId, @PathVariable String seatId) {
        Firestore db = getFireStore();

        Ticket ticket = null;
        try {
            ApiFuture<DocumentSnapshot> future =
                    db.collection("tickets").document(flightId)
                            .collection("seats").document(seatId).get();
            DocumentSnapshot document = future.get();
            ticket = document.toObject(FireBaseTicket.class).toTicket();
        } catch (Exception e) {
            db.shutdown();
            return null;
        }

        db.shutdown();

        return EntityModel.of(ticket);
    }

    @DeleteMapping(path = "/flights/{flightId}/seats/{seatId}/ticket/{ticketid}")
    public EntityModel<Void> deleteTicket(@PathVariable String flightId, @PathVariable String seatId,
                                          @PathVariable String ticketId) {
        Firestore db = getFireStore();

        Ticket ticket = null;
        try {
            db.collection("tickets").document(flightId)
                    .collection("seats").document(seatId).delete();

            Iterable<CollectionReference> collections =
                    db.collection("seats").document(flightId)
                            .collection("seats").document(seatId).listCollections();
            for (CollectionReference collection : collections) {
                if (collection.document(seatId) != null) {
                    ApiFuture<DocumentSnapshot> future = collection.document(seatId).get();
                    DocumentSnapshot document = future.get();
                    collection.document(seatId).update("available",true);
                    break;
                }
            }
        } catch (Exception e) {
            db.shutdown();
            return null;
        }

        db.shutdown();

        return EntityModel.of(null);
    }

}
