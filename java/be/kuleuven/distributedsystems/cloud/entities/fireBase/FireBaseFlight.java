package be.kuleuven.distributedsystems.cloud.entities.fireBase;

import be.kuleuven.distributedsystems.cloud.entities.Flight;

import java.util.UUID;

public class FireBaseFlight {

    private String airline;
    private String flightId;
    private String name;
    private String location;
    private String image;

    public FireBaseFlight(){}

    public FireBaseFlight(String airline, String flightId, String name, String location, String image) {
        this.airline = airline;
        this.flightId = flightId;
        this.name = name;
        this.location = location;
        this.image = image;
    }

    public static FireBaseFlight fromFlight(Flight flight){
        return new FireBaseFlight(
                flight.getAirline(),
                flight.getFlightId().toString(),
                flight.getName(),
                flight.getLocation(),
                flight.getImage()
        );
    }

    public Flight toFlight() {
        return new Flight(
                airline,
                UUID.fromString(flightId),
                name,
                location,
                image
        );
    }

    public String getAirline() {
        return airline;
    }

    public void setAirline(String airline) {
        this.airline = airline;
    }

    public String getFlightId() {
        return flightId;
    }

    public void setFlightId(String flightId) {
        this.flightId = flightId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
