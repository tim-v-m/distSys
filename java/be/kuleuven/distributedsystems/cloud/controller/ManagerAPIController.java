package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.User;
import be.kuleuven.distributedsystems.cloud.entities.fireBase.FireBaseBooking;
import be.kuleuven.distributedsystems.cloud.util.LocalDateAdapter;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;

@RestController
public class ManagerAPIController {

    @Autowired
    String projectId;

    @Autowired
    boolean isProduction;

    @GetMapping("/api/getBookings")
    public String getBookingsAPI() {
        String customer = ( (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .getEmail();

        ArrayList<Booking> bookings = getBookings(customer);

        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateAdapter())
            .create();

        return gson.toJson(bookings);
    }

    @GetMapping("/api/getAllBookings")
    public String getAllBookingsAPI() {
        ArrayList<Booking> bookings = getBookings();

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateAdapter())
                .create();

        return gson.toJson(bookings);
    }

    @GetMapping("/api/getBestCustomers")
    public String getBestCustomerAPI() {

        ArrayList<Booking> bookings = getBookings();

        ArrayList<String> bestCustomers = new ArrayList<>();
        int mostBookings = 0;

        HashMap<String, Integer> customerBookings = new HashMap<String, Integer>();

        for (Booking booking: bookings) {
            customerBookings.put(booking.getCustomer(),
                                    customerBookings.getOrDefault(booking.getCustomer(),0)
                                        + booking.getTickets().size());
        }

        for ( Map.Entry<String,Integer> entry : customerBookings.entrySet()){
            if(entry.getValue() < mostBookings)
                continue;
            else if(entry.getValue() > mostBookings) {
                mostBookings = entry.getValue();
                bestCustomers = new ArrayList<>();
            }

            bestCustomers.add(entry.getKey());
        }

        return new Gson().toJson(bestCustomers.toArray());
    }


    private ArrayList<Booking> getBookings(String customer) {
        ArrayList<Booking> bookings = new ArrayList<>();

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
        ApiFuture<QuerySnapshot> future = db.collection("bookings")
                .document("customers")
                .collection(customer)
                .get();

        try {
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (DocumentSnapshot document : documents) {
                bookings.add(document.toObject(FireBaseBooking.class).toBooking());
            }
        } catch (Exception e){
            System.err.println(e);
        }

        db.shutdown();

        return bookings;
    }

    private ArrayList<Booking> getBookings() {
        ArrayList<Booking> bookings = new ArrayList<>();

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
        Iterable<CollectionReference> collections = db.collection("bookings")
                .document("customers")
                .listCollections();

        for (CollectionReference collRef : collections) {
            ApiFuture<QuerySnapshot> future = collRef
                    .get();

            try {
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                for (DocumentSnapshot document : documents) {
                    bookings.add(document.toObject(FireBaseBooking.class).toBooking());
                }
            } catch (Exception e){}
        }

        db.shutdown();

        return bookings;
    }

}
