package com.cw2.client;

import ds.tutorial.communication.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Scanner;


public class BoxOfficeClient {
    private ManagedChannel channel = null;
    private BoxOfficeServiceGrpc.BoxOfficeServiceBlockingStub boxOfficeStub = null;
    private CustomerServiceGrpc.CustomerServiceBlockingStub customerStub = null;
    private String host = null;
    private int port = -1;

    public BoxOfficeClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeConnection() {
        System.out.println("Initializing connection to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        boxOfficeStub = BoxOfficeServiceGrpc.newBlockingStub(channel);
        customerStub = CustomerServiceGrpc.newBlockingStub(channel);
    }

    public void closeConnection() {
        channel.shutdown();
    }

    public void processUserRequests() throws InterruptedException {
        Scanner userInput = new Scanner(System.in);
        while (true) {
            System.out.println("\n=============================================");
            System.out.println("CONCERT BOOKING SYSTEM - BOX OFFICE MENU");
            System.out.println("=============================================");
            System.out.println("1. View available concerts");
            System.out.println("2. View concert details");
            System.out.println("3. Update concert ticket stock");
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
                    updateTicketStock(userInput);
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

        System.out.println("\nSEAT TIERS AND AVAILABILITY");
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

    private void updateTicketStock(Scanner userInput) {
        System.out.println("\n--- UPDATE TICKET STOCK ---");
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

        
        System.out.println("\nCurrent seat tiers and availability:");
        for (int i = 0; i < show.getSeatTiersCount(); i++) {
            SeatTier tier = show.getSeatTiers(i);
            System.out.println((i+1) + ". " + tier.getType() +
                    " - " + tier.getAvailable() + " seats available");
        }

        System.out.print("Enter seat type to update: ");
        String seatType = userInput.nextLine().trim();

        
        boolean validSeatType = false;
        for (SeatTier tier : show.getSeatTiersList()) {
            if (tier.getType().equalsIgnoreCase(seatType)) {
                validSeatType = true;
                break;
            }
        }

        if (!validSeatType) {
            System.out.println("Error: Seat type '" + seatType + "' not found.");
            return;
        }

        System.out.print("Enter number of additional tickets (use negative for removals): ");
        int additionalTickets = Integer.parseInt(userInput.nextLine().trim());

        int additionalAfterPartyTickets = 0;
        if (show.getHasAfterParty()) {
            System.out.println("Current after-party tickets: " + show.getAfterPartyTickets());
            System.out.print("Enter number of additional after-party tickets (use negative for removals): ");
            additionalAfterPartyTickets = Integer.parseInt(userInput.nextLine().trim());
        }

        
        System.out.println("\nUPDATE SUMMARY");
        System.out.println("====================");
        System.out.println("Concert: " + show.getName());
        System.out.println("Seat Type: " + seatType);
        System.out.println("Additional Tickets: " + additionalTickets);

        if (show.getHasAfterParty()) {
            System.out.println("Additional After-Party Tickets: " + additionalAfterPartyTickets);
        }

        System.out.print("\nConfirm update? (yes/no): ");
        String confirmation = userInput.nextLine().trim();

        if (!confirmation.equalsIgnoreCase("yes")) {
            System.out.println("Update cancelled.");
            return;
        }

        
        UpdateTicketStockRequest request = UpdateTicketStockRequest.newBuilder()
                .setShowId(showId)
                .setSeatType(seatType)
                .setAdditionalTickets(additionalTickets)
                .setAdditionalAfterPartyTickets(additionalAfterPartyTickets)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending update request...");
        UpdateTicketStockResponse response = boxOfficeStub.updateTicketStock(request);

        if (response.getStatus()) {
            System.out.println("Ticket stock update successful!");
        } else {
            System.out.println("Ticket stock update failed: " + response.getMessage());
        }
    }
}