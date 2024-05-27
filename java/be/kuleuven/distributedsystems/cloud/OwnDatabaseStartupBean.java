package be.kuleuven.distributedsystems.cloud;

import be.kuleuven.distributedsystems.cloud.entities.Flight;
import be.kuleuven.distributedsystems.cloud.entities.Seat;
import be.kuleuven.distributedsystems.cloud.entities.fireBase.FireBaseFlight;
import be.kuleuven.distributedsystems.cloud.entities.fireBase.FireBaseSeat;
import be.kuleuven.distributedsystems.cloud.util.Config;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class OwnDatabaseStartupBean {

    @Autowired
    String projectId;

    @Autowired
    boolean isProduction;

    @PostConstruct
    public void initDb() throws IOException, ExecutionException, InterruptedException {

        String data = new String(new ClassPathResource("data.json").getInputStream().readAllBytes());

        FirestoreOptions firestoreOptions = null;

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

        Firestore db = firestoreOptions.getService();
        WriteBatch batch = db.batch();
        ArrayList<WriteBatch> batches = new ArrayList<>();
        int count = 0;



        //check if database is initialised
        //if database already has shows return
        ApiFuture<QuerySnapshot> future = db.collection("flights").get();
        try {
            if (future.get().getDocuments().size() > 0) {
                return;
            }
        } catch (Exception e) {
            return;
        }




        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson( data, JsonObject.class);
        var flightsJson = jsonObject.getAsJsonArray("flights");

        for(int i=0; i < flightsJson.size(); i++){
            JsonObject showJson = flightsJson.get(i).getAsJsonObject();

            //String company, UUID showId, UUID seatId, LocalDateTime time, String type, String name, double price
            UUID flightId = UUID.randomUUID();
            //String company, UUID showId, String name, String location, String image
            Flight flight = new Flight(
                    Config.localStorePseudo,
                    flightId,
                    showJson.get("name").getAsString(),
                    showJson.get("location").getAsString(),
                    showJson.get("image").getAsString()
            );

            batch.set(
                    db.collection("flights").document(flight.getFlightId().toString()),
                    FireBaseFlight.fromFlight(flight)
            );
            count++;
            if(count >= 500) {
                count = 0;
                batches.add(batch);
                batch = db.batch();
            }

            JsonArray seatsJson = flightsJson.get(i).getAsJsonObject().get("seats").getAsJsonArray();

            for(int j=0; j < seatsJson.size(); j++){
                //String company, UUID showId, UUID seatId, LocalDateTime time, String type, String name, double price
                String time = seatsJson.get(j).getAsJsonObject().get("time").getAsString();
                time = LocalDateTime.parse(time).toString();

                Seat seat = new Seat(
                        Config.localStorePseudo,
                        flightId,
                        UUID.randomUUID(),
                        LocalDateTime.parse(seatsJson.get(j).getAsJsonObject().get("time").getAsString()),
                        seatsJson.get(j).getAsJsonObject().get("type").getAsString(),
                        seatsJson.get(j).getAsJsonObject().get("name").getAsString(),
                        seatsJson.get(j).getAsJsonObject().get("price").getAsDouble()
                );

                /*batch.set(
                        db.collection("times").document(flight.getFlightId().toString())
                                .collection(time).document(seat.getSeatId().toString()),
                        null
                );
                count++;
                if(count >= 500) {
                    count = 0;
                    batches.add(batch);
                    batch = db.batch();
                }*/


                batch.set(
                        db.collection("seats").document(flight.getFlightId().toString())
                                .collection(time).document(seat.getSeatId().toString()),
                        FireBaseSeat.fromSeat(seat)
                );
                count++;
                if(count >= 500) {
                    count = 0;
                    batches.add(batch);
                    batch = db.batch();
                }
            }

        }

        try {
            for(WriteBatch writeBatch: batches) {
                writeBatch.commit().get();
            }
        } catch (Exception e) {
            System.err.println(e);
        }
        System.out.println("firestore ready");
    }

}
