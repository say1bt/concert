package com.cw2.client;

import ds.tutorial.communication.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Scanner;

public class BulkBookingClient {
    private ManagedChannel channel = null;
    private CustomerServiceGrpc.CustomerServiceBlockingStub customerStub = null;
    private String host = null;
    private int port = -1;

    public BulkBookingClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeConnection() {
        System.out.println("Initializing connection to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        customerStub = CustomerServiceGrpc.newBlockingStub(channel);
    }

    public void closeConnection() {
        channel.shutdown();
    }

    public void processUserRequests() throws InterruptedException {
        Scanner userInput = new Scanner(System.in);
        while (true) {
            System.out.println("\n=============================================");
            System.out.println("CONCERT BOOKING SYSTEM - EVENT COORDINATOR MENU");
            System.out.println("=============================================");
            System.out.println("1. View available concerts");
            System.out.println("2. View concert details");
            System.out.println("3. Book bulk tickets for groups");
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
                    bookBulkTickets(userInput);
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

        GetConcertRequest request = GetConcertRequest.newBuilder()
                .setShowId(showId)
                .build();

        GetConcertResponse response = customerStub.getConcert(request);

        if (response.getShow() == null || response.getShow().getId().isEmpty()) {
            System.out.println("Concert not found with ID: " + showId);
            return;
        }

        ConcertShow show = response.getShow();

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

    private void bookBulkTickets(Scanner userInput) {
        System.out.println("\n--- BULK TICKET BOOKING FOR GROUPS ---");
        System.out.print("Enter concert ID: ");
        String showId = userInput.nextLine().trim();

        
        GetConcertRequest getConcertRequest = GetConcertRequest.newBuilder()
                .setShowId(showId)
                .build();

        GetConcertResponse getConcertResponse = customerStub.getConcert(getConcertRequest);

        if (getConcertResponse.getShow() == null || getConcertResponse.getShow().getId().isEmpty()) {
            System.out.println("Concert not found with ID: " + showId);
            return;
        }

        ConcertShow show = getConcertResponse.getShow();

        System.out.print("Enter group name or organization: ");
        String groupName = userInput.nextLine().trim();

        
        System.out.println("\nAvailable seat tiers:");
        for (int i = 0; i < show.getSeatTiersCount(); i++) {
            SeatTier tier = show.getSeatTiers(i);
            System.out.println((i+1) + ". " + tier.getType() +
                    " - " + tier.getAvailable() + " seats available at $" +
                    String.format("%.2f", tier.getPrice()));
        }

        System.out.print("Enter seat type: ");
        String seatType = userInput.nextLine().trim();

        System.out.print("Enter number of tickets for the group: ");
        int quantity = Integer.parseInt(userInput.nextLine().trim());

        boolean includeAfterParty = false;

        if (show.getHasAfterParty() && show.getAfterPartyTickets() > 0) {
            System.out.print("Would you like to include after-party tickets for the group? (yes/no): ");
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

        
        double discountRate = 0.0;
        if (quantity >= 20) {
            discountRate = 0.15; 
        } else if (quantity >= 10) {
            discountRate = 0.10; 
        } else if (quantity >= 5) {
            discountRate = 0.05; 
        }

        double discountAmount = totalCost * discountRate;
        double discountedTotal = totalCost - discountAmount;

        
        System.out.println("\nBULK BOOKING SUMMARY");
        System.out.println("====================");
        System.out.println("Concert: " + show.getName());
        System.out.println("Date: " + show.getDate());
        System.out.println("Group Name: " + groupName);
        System.out.println("Seat Type: " + seatType);
        System.out.println("Number of Tickets: " + quantity);

        if (includeAfterParty) {
            System.out.println("After-Party Tickets: Included");
        }

        System.out.println("Subtotal: $" + String.format("%.2f", totalCost));
        if (discountRate > 0) {
            System.out.println("Group Discount (" + (discountRate * 100) + "%): -$" + String.format("%.2f", discountAmount));
            System.out.println("Total Cost: $" + String.format("%.2f", discountedTotal));
        } else {
            System.out.println("Total Cost: $" + String.format("%.2f", totalCost));
        }

        System.out.print("\nConfirm bulk booking? (yes/no): ");
        String confirmation = userInput.nextLine().trim();

        if (!confirmation.equalsIgnoreCase("yes")) {
            System.out.println("Booking cancelled.");
            return;
        }

        
        
        String customerId = "GROUP-" + groupName.replaceAll("\\s+", "-") + "-" + System.currentTimeMillis();

        ReserveTicketRequest request = ReserveTicketRequest.newBuilder()
                .setShowId(showId)
                .setSeatType(seatType)
                .setQuantity(quantity)
                .setIncludeAfterParty(includeAfterParty)
                .setIsSentByPrimary(false)
                .setCustomerId(customerId)
                .build();

        System.out.println("Sending bulk booking request...");
        ReserveTicketResponse response = customerStub.reserveTicket(request);

        if (response.getStatus()) {
            System.out.println("Bulk booking successful! Reservation ID: " + response.getReservationId());
        } else {
            System.out.println("Bulk booking failed: " + response.getMessage());
        }
    }
}