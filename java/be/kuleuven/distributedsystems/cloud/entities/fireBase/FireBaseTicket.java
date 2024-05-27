package be.kuleuven.distributedsystems.cloud.entities.fireBase;

import be.kuleuven.distributedsystems.cloud.entities.Ticket;

import java.util.UUID;

public class FireBaseTicket {
    private String airline;
    private String flightId;
    private String seatId;
    private String ticketId;
    private String customer;
    private String bookingReference;

    public FireBaseTicket() {
    }

    public FireBaseTicket(String airline, String flightId, String seatId, String ticketId, String customer, String bookingReference) {
        this.airline = airline;
        this.flightId = flightId;
        this.seatId = seatId;
        this.ticketId = ticketId;
        this.customer = customer;
        this.bookingReference = bookingReference;
    }

    public String getAirline() {
        return airline;
    }

    public String getFlightId() {
        return flightId;
    }

    public String getSeatId() {
        return this.seatId;
    }

    public String getTicketId() {
        return this.ticketId;
    }

    public String getCustomer() {
        return this.customer;
    }

    public String getBookingReference() {
        return this.bookingReference;
    }

    public void setAirline(String airline) {
        this.airline = airline;
    }

    public void setFlightId(String flightId) {
        this.flightId = flightId;
    }

    public void setSeatId(String seatId) {
        this.seatId = seatId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public static FireBaseTicket fromTicket(Ticket ticket) {
        return new FireBaseTicket(
                ticket.getAirline(),
                ticket.getFlightId().toString(),
                ticket.getSeatId().toString(),
                ticket.getTicketId().toString(),
                ticket.getCustomer(),
                ticket.getBookingReference()
        );
    }

    public Ticket toTicket() {
        return  new Ticket(
                this.airline,
                UUID.fromString(this.flightId),
                UUID.fromString(this.seatId),
                UUID.fromString(this.ticketId),
                this.customer,
                this.bookingReference
        );
    }

}
