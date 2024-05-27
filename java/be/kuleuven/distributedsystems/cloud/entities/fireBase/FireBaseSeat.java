package be.kuleuven.distributedsystems.cloud.entities.fireBase;

import be.kuleuven.distributedsystems.cloud.entities.Seat;

import java.time.LocalDateTime;
import java.util.UUID;

public class FireBaseSeat {
    private String airline;
    private String flightId;
    private String seatId;
    private String time;
    private String type;
    private String name;
    private double price;
    private boolean available;

    public FireBaseSeat() {
        this.available = true;
    }

    public FireBaseSeat(String airline, String flightId, String seatId, String time, String type, String name, double price) {
        this.airline = airline;
        this.flightId = flightId;
        this.seatId = seatId;
        this.time = time;
        this.type = type;
        this.name = name;
        this.price = price;
        this.available = true;
    }

    public static FireBaseSeat fromSeat(Seat seat) {
        return new FireBaseSeat(
                seat.getAirline(),
                seat.getFlightId().toString(),
                seat.getSeatId().toString(),
                seat.getTime().toString(),
                seat.getType(),
                seat.getName(),
                seat.getPrice()
        );
    }

    public Seat toSeat() {
        return new Seat(
                airline,
                UUID.fromString(flightId),
                UUID.fromString(seatId),
                LocalDateTime.parse(time),
                type,
                name,
                price
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

    public String getSeatId() {
        return seatId;
    }

    public void setSeatId(String seatId) {
        this.seatId = seatId;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
