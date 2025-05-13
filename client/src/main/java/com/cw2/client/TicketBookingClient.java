package com.cw2.client;

import ds.tutorial.communication.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Scanner;

public class TicketBookingClient {
    private ManagedChannel channel = null;
    CustomerServiceGrpc.CustomerServiceBlockingStub customerStub = null;
    ConcertOrganizerServiceGrpc.ConcertOrganizerServiceBlockingStub organizerStub = null;
    BoxOfficeServiceGrpc.BoxOfficeServiceBlockingStub boxOfficeStub = null;
    String host = null;
    int port = -1;

    public TicketBookingClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeConnection() {
        System.out.println("Initializing connection to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        customerStub = CustomerServiceGrpc.newBlockingStub(channel);
        organizerStub = ConcertOrganizerServiceGrpc.newBlockingStub(channel);
        boxOfficeStub = BoxOfficeServiceGrpc.newBlockingStub(channel);
    }

    public void closeConnection() {
        channel.shutdown();
    }

    public void processUserRequests() throws InterruptedException {
        Scanner userInput = new Scanner(System.in);
        while (true) {
            System.out.println("\n=============================================");
            System.out.println("CONCERT TICKET BOOKING SYSTEM - CUSTOMER MENU");
            System.out.println("=============================================");
            System.out.println("1. View available concerts");
            System.out.println("2. View concert details");
            System.out.println("3. Reserve tickets");
            System.out.println("0. Exit");
            System.out.print("Enter your choice: ");

            int choice = Integer.parseInt(userInput.nextLine().trim());

            switch (choice) {
                case 0:
                    return;
                case 1:
                    viewAvailableConcerts();
                    break;
                case 2:
                    viewConcertDetails(userInput);
                    break;
                case 3:
                    reserveTickets(userInput);
                    break;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
            Thread.sleep(1000);
        }
    }

    private void viewAvailableConcerts() {
        System.out.println("\n--- AVAILABLE CONCERTS ---");

        ListConcertsRequest request = ListConcertsRequest.newBuilder().build();
        ListConcertsResponse response = customerStub.listConcerts(request);

        if (response.getShowsCount() == 0) {
            System.out.println("No concerts available.");
            return;
        }

        System.out.println("Available concerts:");
        System.out.println("-------------------");
        for (ConcertShow show : response.getShowsList()) {
            System.out.println("ID: " + show.getId());
            System.out.println("Name: " + show.getName());
            System.out.println("Date: " + show.getDate());
            System.out.println("Venue: " + show.getVenue());
            System.out.println("Has After-Party: " + (show.getHasAfterParty() ? "Yes" : "No"));
            System.out.println("-------------------");
        }
    }

    private void viewConcertDetails(Scanner userInput) {
        System.out.println("\n--- CONCERT DETAILS ---");
        System.out.print("Enter concert ID: ");
        String showId = userInput.nextLine().trim();

        
        ListConcertsRequest listRequest = ListConcertsRequest.newBuilder().build();
        ListConcertsResponse listResponse = customerStub.listConcerts(listRequest);

        ConcertShow show = null;
        for (ConcertShow concert : listResponse.getShowsList()) {
            if (concert.getId().equals(showId)) {
                show = concert;
                break;
            }
        }

        if (show == null) {
            System.out.println("Concert not found with ID: " + showId);
            return;
        }

        System.out.println("\nCONCERT DETAILS");
        System.out.println("====================");
        System.out.println("Name: " + show.getName());
        System.out.println("Date: " + show.getDate());
        System.out.println("Venue: " + show.getVenue());
        System.out.println("Description: " + show.getDescription());

        System.out.println("\nAVAILABLE SEAT TIERS");
        System.out.println("====================");
        for (SeatTier tier : show.getSeatTiersList()) {
            System.out.println(tier.getType() + " - "
                    + tier.getAvailable() + " seats available at $"
                    + String.format("%.2f", tier.getPrice()) + " each");
        }

        if (show.getHasAfterParty()) {
            System.out.println("\nAFTER-PARTY");
            System.out.println("====================");
            System.out.println("Available tickets: " + show.getAfterPartyTickets());
        }
    }

    private void reserveTickets(Scanner userInput) {
        System.out.println("\n--- RESERVE CONCERT TICKETS ---");
        System.out.print("Enter concert ID: ");
        String showId = userInput.nextLine().trim();

        ListConcertsRequest listRequest = ListConcertsRequest.newBuilder().build();
        ListConcertsResponse listResponse = customerStub.listConcerts(listRequest);

        ConcertShow show = null;
        for (ConcertShow concert : listResponse.getShowsList()) {
            if (concert.getId().equals(showId)) {
                show = concert;
                break;
            }
        }

        if (show == null) {
            System.out.println("Concert not found with ID: " + showId);
            return;
        }

        System.out.print("Enter your customer ID: ");
        String customerId = userInput.nextLine().trim();

        System.out.println("\nAvailable seat tiers:");
        for (int i = 0; i < show.getSeatTiersCount(); i++) {
            SeatTier tier = show.getSeatTiers(i);
            System.out.println((i+1) + ". " + tier.getType() +
                    " - " + tier.getAvailable() + " seats available at $" +
                    String.format("%.2f", tier.getPrice()));
        }

        System.out.print("Enter seat type: ");
        String seatType = userInput.nextLine().trim();

        System.out.print("Enter number of tickets: ");
        int quantity = Integer.parseInt(userInput.nextLine().trim());

        boolean includeAfterParty = false;

        if (show.getHasAfterParty() && show.getAfterPartyTickets() > 0) {
            System.out.print("Would you like to include after-party tickets? (yes/no): ");
            includeAfterParty = userInput.nextLine().trim().equalsIgnoreCase("yes");
        }

        double totalCost = 0.0;

        boolean foundTier = false;
        for (SeatTier tier : show.getSeatTiersList()) {
            if (tier.getType().equalsIgnoreCase(seatType)) {
                totalCost += tier.getPrice() * quantity;
                foundTier = true;
                break;
            }
        }

        if (!foundTier) {
            System.out.println("Error: Seat type '" + seatType + "' not found.");
            return;
        }

        System.out.println("\nRESERVATION SUMMARY");
        System.out.println("====================");
        System.out.println("Concert: " + show.getName());
        System.out.println("Date: " + show.getDate());
        System.out.println("Seat Type: " + seatType);
        System.out.println("Number of Tickets: " + quantity);

        if (includeAfterParty) {
            System.out.println("After-Party Tickets: Included");
        }

        System.out.println("Total Cost: $" + String.format("%.2f", totalCost));
        System.out.print("\nConfirm reservation? (yes/no): ");
        String confirmation = userInput.nextLine().trim();

        if (!confirmation.equalsIgnoreCase("yes")) {
            System.out.println("Reservation cancelled.");
            return;
        }

        ReserveTicketRequest request = ReserveTicketRequest.newBuilder()
                .setShowId(showId)
                .setCustomerId(customerId)
                .setSeatType(seatType)
                .setQuantity(quantity)
                .setIncludeAfterParty(includeAfterParty)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Request details: " +
                "ShowID: " + request.getShowId() +
                ", CustomerID: " + request.getCustomerId() +
                ", SeatType: " + request.getSeatType() +
                ", Quantity: " + request.getQuantity());
        System.out.println("Sending reservation request...");
        ReserveTicketResponse response = customerStub.reserveTicket(request);
        System.out.println("response..." + response.toString());

        if (response.getStatus()) {
            System.out.println("Reservation successful! Reservation ID: " + response.getReservationId());
        } else {
            System.out.println("Reservation failed: " + response.getMessage());
        }
    }
}