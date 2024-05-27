package be.kuleuven.distributedsystems.cloud.entities.fireBase;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FireBaseBooking {
    private String id;
    private String time;
    private List<FireBaseTicket> tickets;
    private String customer;

    public FireBaseBooking() {
    }

    public FireBaseBooking(String id, String time, List<FireBaseTicket> tickets, String customer) {
        this.id = id;
        this.time = time;
        this.tickets = tickets;
        this.customer = customer;
    }

    public String getId() {
        return this.id;
    }

    public String getTime() {
        return this.time;
    }

    public List<FireBaseTicket> getTickets() {
        return this.tickets;
    }

    public String getCustomer() {
        return this.customer;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void setTickets(List<FireBaseTicket> tickets) {
        this.tickets = tickets;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public static FireBaseBooking fromBooking(Booking booking) {

        ArrayList<FireBaseTicket> fireBaseTickets = new ArrayList<>();
        for (Ticket ticket : booking.getTickets() ) {
            fireBaseTickets.add(FireBaseTicket.fromTicket(ticket));
        }

        return new FireBaseBooking(
            booking.getId().toString(),
            booking.getTime().toString(),
            fireBaseTickets,
            booking.getCustomer()
        );
    }

    public Booking toBooking() {
        ArrayList<Ticket> tickets = new ArrayList<>();
        for (FireBaseTicket fireBaseTicket : this.tickets ) {
            tickets.add(fireBaseTicket.toTicket());
        }

        return new Booking(
                UUID.fromString(this.id),
                LocalDateTime.parse(this.time),
                tickets,
                this.getCustomer()
        );
    }
}
