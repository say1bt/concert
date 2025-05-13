package ds.tutorials.communication.client;

import ds.tutorial.communication.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Scanner;

public class TicketBookingClient {
    private ManagedChannel channel = null;
    TicketBookingServiceGrpc.TicketBookingServiceBlockingStub clientStub = null;
    ConcertManagementServiceGrpc.ConcertManagementServiceBlockingStub managementStub = null;
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
        clientStub = TicketBookingServiceGrpc.newBlockingStub(channel);
        managementStub = ConcertManagementServiceGrpc.newBlockingStub(channel);
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
            System.out.println("3. Book tickets");
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
                    bookTickets(userInput);
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
        ListConcertsResponse response = managementStub.listConcerts(request);

        if (response.getConcertsCount() == 0) {
            System.out.println("No concerts available.");
            return;
        }

        System.out.println("Available concerts:");
        System.out.println("-------------------");
        for (Concert concert : response.getConcertsList()) {
            if (!concert.getIsCancelled()) {
                System.out.println("ID: " + concert.getConcertId());
                System.out.println("Name: " + concert.getName());
                System.out.println("Date: " + concert.getDate());
                System.out.println("Location: " + concert.getLocation());
                System.out.println("Has After-Party: " + (concert.getHasAfterParty() ? "Yes" : "No"));
                System.out.println("-------------------");
            }
        }
    }

    private void viewConcertDetails(Scanner userInput) {
        System.out.println("\n--- CONCERT DETAILS ---");
        System.out.print("Enter concert ID: ");
        String concertId = userInput.nextLine().trim();

        GetConcertRequest request = GetConcertRequest.newBuilder()
                .setConcertId(concertId)
                .build();

        GetConcertResponse response = managementStub.getConcert(request);

        if (response.getConcert() == null || response.getConcert().getConcertId().isEmpty()) {
            System.out.println("Concert not found with ID: " + concertId);
            return;
        }

        Concert concert = response.getConcert();

        if (concert.getIsCancelled()) {
            System.out.println("This concert has been cancelled.");
            return;
        }

        System.out.println("\nCONCERT DETAILS");
        System.out.println("====================");
        System.out.println("Name: " + concert.getName());
        System.out.println("Date: " + concert.getDate());
        System.out.println("Location: " + concert.getLocation());

        System.out.println("\nAVAILABLE SEAT TIERS");
        System.out.println("====================");
        for (SeatTier tier : concert.getSeatTiersList()) {
            System.out.println(tier.getTierName() + " - "
                    + tier.getAvailableSeats() + " seats available at $"
                    + String.format("%.2f", tier.getPrice()) + " each");
        }

        if (concert.getHasAfterParty()) {
            System.out.println("\nAFTER-PARTY");
            System.out.println("====================");
            System.out.println("Available tickets: " + concert.getAfterPartyTicketsAvailable());
            System.out.println("Price: $" + String.format("%.2f", concert.getAfterPartyTicketPrice()));
        }
    }

    private void bookTickets(Scanner userInput) {
        System.out.println("\n--- BOOK CONCERT TICKETS ---");
        System.out.print("Enter concert ID: ");
        String concertId = userInput.nextLine().trim();

        // First check if the concert exists and is not cancelled
        GetConcertRequest getConcertRequest = GetConcertRequest.newBuilder()
                .setConcertId(concertId)
                .build();

        GetConcertResponse getConcertResponse = managementStub.getConcert(getConcertRequest);

        if (getConcertResponse.getConcert() == null || getConcertResponse.getConcert().getConcertId().isEmpty()) {
            System.out.println("Concert not found with ID: " + concertId);
            return;
        }

        Concert concert = getConcertResponse.getConcert();

        if (concert.getIsCancelled()) {
            System.out.println("This concert has been cancelled. No tickets available.");
            return;
        }

        System.out.print("Enter your name: ");
        String customerName = userInput.nextLine().trim();

        // Display available seat tiers
        System.out.println("\nAvailable seat tiers:");
        for (int i = 0; i < concert.getSeatTiersCount(); i++) {
            SeatTier tier = concert.getSeatTiers(i);
            System.out.println((i+1) + ". " + tier.getTierName() +
                    " - " + tier.getAvailableSeats() + " seats available at $" +
                    String.format("%.2f", tier.getPrice()));
        }

        System.out.print("Enter seat tier name: ");
        String seatTier = userInput.nextLine().trim();

        System.out.print("Enter number of tickets: ");
        int numTickets = Integer.parseInt(userInput.nextLine().trim());

        boolean includeAfterParty = false;
        int numAfterPartyTickets = 0;

        if (concert.getHasAfterParty() && concert.getAfterPartyTicketsAvailable() > 0) {
            System.out.print("Would you like to include after-party tickets? (yes/no): ");
            includeAfterParty = userInput.nextLine().trim().equalsIgnoreCase("yes");

            if (includeAfterParty) {
                System.out.print("Enter number of after-party tickets (must be <= number of concert tickets): ");
                numAfterPartyTickets = Integer.parseInt(userInput.nextLine().trim());

                if (numAfterPartyTickets > numTickets) {
                    System.out.println("Error: Number of after-party tickets cannot exceed number of concert tickets.");
                    return;
                }
            }
        }

        // Calculate total cost for confirmation
        double totalCost = 0.0;

        boolean foundTier = false;
        for (SeatTier tier : concert.getSeatTiersList()) {
            if (tier.getTierName().equalsIgnoreCase(seatTier)) {
                totalCost += tier.getPrice() * numTickets;
                foundTier = true;
                break;
            }
        }

        if (!foundTier) {
            System.out.println("Error: Seat tier '" + seatTier + "' not found.");
            return;
        }

        if (includeAfterParty) {
            totalCost += concert.getAfterPartyTicketPrice() * numAfterPartyTickets;
        }

        // Confirm booking
        System.out.println("\nBOOKING SUMMARY");
        System.out.println("====================");
        System.out.println("Concert: " + concert.getName());
        System.out.println("Date: " + concert.getDate());
        System.out.println("Seat Tier: " + seatTier);
        System.out.println("Number of Tickets: " + numTickets);

        if (includeAfterParty) {
            System.out.println("After-Party Tickets: " + numAfterPartyTickets);
        }

        System.out.println("Total Cost: $" + String.format("%.2f", totalCost));
        System.out.print("\nConfirm booking? (yes/no): ");
        String confirmation = userInput.nextLine().trim();

        if (!confirmation.equalsIgnoreCase("yes")) {
            System.out.println("Booking cancelled.");
            return;
        }

        // Send booking request
        BookTicketsRequest request = BookTicketsRequest.newBuilder()
                .setConcertId(concertId)
                .setCustomerName(customerName)
                .setSeatTier(seatTier)
                .setNumTickets(numTickets)
                .setIncludeAfterParty(includeAfterParty)
                .setNumAfterPartyTickets(numAfterPartyTickets)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending booking request...");
        BookTicketsResponse response = clientStub.bookTickets(request);

        if (response.getStatus()) {
            System.out.println("Booking successful! Booking ID: " + response.getBookingId());
        } else {
            System.out.println("Booking failed: " + response.getMessage());
        }
    }
}