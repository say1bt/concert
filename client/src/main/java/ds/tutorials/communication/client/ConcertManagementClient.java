package ds.tutorials.communication.client;

import ds.tutorial.communication.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Scanner;
import java.util.UUID;

public class ConcertManagementClient {
    private ManagedChannel channel = null;
    private ConcertOrganizerServiceGrpc.ConcertOrganizerServiceBlockingStub organizerStub = null;
    private BoxOfficeServiceGrpc.BoxOfficeServiceBlockingStub boxOfficeStub = null;
    private CustomerServiceGrpc.CustomerServiceBlockingStub customerStub = null;
    private String host = null;
    private int port = -1;

    public ConcertManagementClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeConnection() {
        System.out.println("Initializing connection to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        organizerStub = ConcertOrganizerServiceGrpc.newBlockingStub(channel);
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
            System.out.println("CONCERT MANAGEMENT SYSTEM - ORGANIZER MENU");
            System.out.println("=============================================");
            System.out.println("1. Add new concert");
            System.out.println("2. Update concert details");
            System.out.println("3. Cancel concert");
            System.out.println("4. List all concerts");
            System.out.println("5. View concert details");
            System.out.println("6. Update ticket stock (Box Office)");
            System.out.println("7. Reserve tickets (Customer)");
            System.out.println("0. Exit");
            System.out.print("Enter your choice: ");

            int choice = Integer.parseInt(userInput.nextLine().trim());

            switch (choice) {
                case 0:
                    return;
                case 1:
                    addNewConcert(userInput);
                    break;
                case 2:
                    updateConcertDetails(userInput);
                    break;
                case 3:
                    cancelConcert(userInput);
                    break;
                case 4:
                    listAllConcerts();
                    break;
                case 5:
                    viewConcertDetails(userInput);
                    break;
                case 6:
                    updateTicketStock(userInput);
                    break;
                case 7:
                    reserveTickets(userInput);
                    break;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
            Thread.sleep(1000);
        }
    }

    private void addNewConcert(Scanner userInput) {
        System.out.println("\n--- ADD NEW CONCERT ---");
        System.out.print("Enter concert name: ");
        String name = userInput.nextLine().trim();

        System.out.print("Enter concert date (YYYY-MM-DD): ");
        String date = userInput.nextLine().trim();

        System.out.print("Enter venue: ");
        String venue = userInput.nextLine().trim();

        System.out.print("Enter description: ");
        String description = userInput.nextLine().trim();

        System.out.print("Does this concert have an after-party? (yes/no): ");
        boolean hasAfterParty = userInput.nextLine().trim().equalsIgnoreCase("yes");

        int afterPartyTickets = 0;
        if (hasAfterParty) {
            System.out.print("Enter number of after-party tickets available: ");
            afterPartyTickets = Integer.parseInt(userInput.nextLine().trim());
        }

        System.out.print("How many seat tiers does this concert have? ");
        int numTiers = Integer.parseInt(userInput.nextLine().trim());

        ConcertShow.Builder showBuilder = ConcertShow.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setName(name)
                .setDate(date)
                .setVenue(venue)
                .setDescription(description)
                .setHasAfterParty(hasAfterParty)
                .setAfterPartyTickets(afterPartyTickets);

        for (int i = 0; i < numTiers; i++) {
            System.out.println("\n--- SEAT TIER " + (i+1) + " ---");
            System.out.print("Enter tier type (e.g., Regular, VIP): ");
            String tierType = userInput.nextLine().trim();

            System.out.print("Enter number of available seats: ");
            int availableSeats = Integer.parseInt(userInput.nextLine().trim());

            System.out.print("Enter ticket price for this tier: ");
            double price = Double.parseDouble(userInput.nextLine().trim());

            SeatTier seatTier = SeatTier.newBuilder()
                    .setType(tierType)
                    .setAvailable(availableSeats)
                    .setPrice(price)
                    .build();

            showBuilder.addSeatTiers(seatTier);
        }

        ConcertShow show = showBuilder.build();
        AddConcertRequest request = AddConcertRequest.newBuilder()
                .setShow(show)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending request to add new concert...");
        AddConcertResponse response = organizerStub.addConcert(request);

        if (response.getStatus()) {
            System.out.println("Concert added successfully!");
        } else {
            System.out.println("Failed to add concert: " + response.getMessage());
        }
    }

    private void updateConcertDetails(Scanner userInput) {
        System.out.println("\n--- UPDATE CONCERT DETAILS ---");
        System.out.print("Enter concert ID to update: ");
        String showId = userInput.nextLine().trim();

        
        GetConcertRequest getConcertRequest = GetConcertRequest.newBuilder()
                .setShowId(showId)
                .build();

        GetConcertResponse getConcertResponse = customerStub.getConcert(getConcertRequest);
        if (getConcertResponse.getShow() == null || getConcertResponse.getShow().getId().isEmpty()) {
            System.out.println("Concert not found with ID: " + showId);
            return;
        }

        ConcertShow oldShow = getConcertResponse.getShow();
        ConcertShow.Builder updatedShowBuilder = oldShow.toBuilder();

        System.out.println("Current details: " + oldShow.getName() + " at " + oldShow.getVenue() + " on " + oldShow.getDate());

        System.out.print("Enter new name (or press Enter to keep current): ");
        String newName = userInput.nextLine().trim();
        if (!newName.isEmpty()) {
            updatedShowBuilder.setName(newName);
        }

        System.out.print("Enter new date (YYYY-MM-DD) (or press Enter to keep current): ");
        String newDate = userInput.nextLine().trim();
        if (!newDate.isEmpty()) {
            updatedShowBuilder.setDate(newDate);
        }

        System.out.print("Enter new venue (or press Enter to keep current): ");
        String newVenue = userInput.nextLine().trim();
        if (!newVenue.isEmpty()) {
            updatedShowBuilder.setVenue(newVenue);
        }

        System.out.print("Enter new description (or press Enter to keep current): ");
        String newDescription = userInput.nextLine().trim();
        if (!newDescription.isEmpty()) {
            updatedShowBuilder.setDescription(newDescription);
        }

        System.out.print("Update after-party status? (yes/no): ");
        String updateAfterParty = userInput.nextLine().trim();
        if (updateAfterParty.equalsIgnoreCase("yes")) {
            System.out.print("Does this concert have an after-party? (yes/no): ");
            boolean hasAfterParty = userInput.nextLine().trim().equalsIgnoreCase("yes");
            updatedShowBuilder.setHasAfterParty(hasAfterParty);

            if (hasAfterParty) {
                System.out.print("Enter number of after-party tickets available: ");
                int afterPartyTickets = Integer.parseInt(userInput.nextLine().trim());
                updatedShowBuilder.setAfterPartyTickets(afterPartyTickets);
            } else {
                updatedShowBuilder.setAfterPartyTickets(0);
            }
        }

        System.out.print("Update seat tiers? (yes/no): ");
        String updateSeatTiers = userInput.nextLine().trim();
        if (updateSeatTiers.equalsIgnoreCase("yes")) {
            updatedShowBuilder.clearSeatTiers();

            System.out.print("How many seat tiers will this concert have? ");
            int numTiers = Integer.parseInt(userInput.nextLine().trim());

            for (int i = 0; i < numTiers; i++) {
                System.out.println("\n--- SEAT TIER " + (i+1) + " ---");
                System.out.print("Enter tier type (e.g., Regular, VIP): ");
                String tierType = userInput.nextLine().trim();

                System.out.print("Enter number of available seats: ");
                int availableSeats = Integer.parseInt(userInput.nextLine().trim());

                System.out.print("Enter ticket price for this tier: ");
                double price = Double.parseDouble(userInput.nextLine().trim());

                SeatTier seatTier = SeatTier.newBuilder()
                        .setType(tierType)
                        .setAvailable(availableSeats)
                        .setPrice(price)
                        .build();

                updatedShowBuilder.addSeatTiers(seatTier);
            }
        }

        ConcertShow updatedShow = updatedShowBuilder.build();
        UpdateConcertRequest request = UpdateConcertRequest.newBuilder()
                .setShowId(showId)
                .setUpdatedShow(updatedShow)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending request to update concert...");
        UpdateConcertResponse response = organizerStub.updateConcert(request);

        if (response.getStatus()) {
            System.out.println("Concert updated successfully!");
        } else {
            System.out.println("Failed to update concert: " + response.getMessage());
        }
    }

    private void cancelConcert(Scanner userInput) {
        System.out.println("\n--- CANCEL CONCERT ---");
        System.out.print("Enter concert ID to cancel: ");
        String showId = userInput.nextLine().trim();

        System.out.print("Are you sure you want to cancel this concert? (yes/no): ");
        String confirmation = userInput.nextLine().trim();

        if (!confirmation.equalsIgnoreCase("yes")) {
            System.out.println("Cancellation aborted.");
            return;
        }

        CancelConcertRequest request = CancelConcertRequest.newBuilder()
                .setShowId(showId)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending request to cancel concert...");
        CancelConcertResponse response = organizerStub.cancelConcert(request);

        if (response.getStatus()) {
            System.out.println("Concert cancelled successfully!");
        } else {
            System.out.println("Failed to cancel concert: " + response.getMessage());
        }
    }

    private void listAllConcerts() {
        System.out.println("\n--- LIST ALL CONCERTS ---");

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
        System.out.println("\n--- VIEW CONCERT DETAILS ---");
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
        System.out.println("ID: " + show.getId());
        System.out.println("Name: " + show.getName());
        System.out.println("Date: " + show.getDate());
        System.out.println("Venue: " + show.getVenue());
        System.out.println("Description: " + show.getDescription());

        System.out.println("\nSEAT TIERS");
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
        System.out.println("\n--- UPDATE TICKET STOCK (BOX OFFICE) ---");
        System.out.print("Enter concert ID: ");
        String showId = userInput.nextLine().trim();

        System.out.print("Enter seat type to update (e.g., Regular, VIP): ");
        String seatType = userInput.nextLine().trim();

        System.out.print("Enter number of additional tickets (can be negative to reduce): ");
        int additionalTickets = Integer.parseInt(userInput.nextLine().trim());

        System.out.print("Enter number of additional after-party tickets (can be negative to reduce): ");
        int additionalAfterPartyTickets = Integer.parseInt(userInput.nextLine().trim());

        UpdateTicketStockRequest request = UpdateTicketStockRequest.newBuilder()
                .setShowId(showId)
                .setSeatType(seatType)
                .setAdditionalTickets(additionalTickets)
                .setAdditionalAfterPartyTickets(additionalAfterPartyTickets)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending request to update ticket stock...");
        UpdateTicketStockResponse response = boxOfficeStub.updateTicketStock(request);

        if (response.getStatus()) {
            System.out.println("Ticket stock updated successfully!");
        } else {
            System.out.println("Failed to update ticket stock: " + response.getMessage());
        }
    }

    private void reserveTickets(Scanner userInput) {
        System.out.println("\n--- RESERVE TICKETS (CUSTOMER) ---");
        System.out.print("Enter customer ID: ");
        String customerId = userInput.nextLine().trim();

        System.out.print("Enter concert ID: ");
        String showId = userInput.nextLine().trim();

        System.out.print("Enter seat type (e.g., Regular, VIP): ");
        String seatType = userInput.nextLine().trim();

        System.out.print("Enter number of tickets to reserve: ");
        int quantity = Integer.parseInt(userInput.nextLine().trim());

        System.out.print("Include after-party tickets? (yes/no): ");
        boolean includeAfterParty = userInput.nextLine().trim().equalsIgnoreCase("yes");

        ReserveTicketRequest request = ReserveTicketRequest.newBuilder()
                .setShowId(showId)
                .setSeatType(seatType)
                .setQuantity(quantity)
                .setIncludeAfterParty(includeAfterParty)
                .setIsSentByPrimary(false)
                .setCustomerId(customerId)
                .build();

        System.out.println("Sending request to reserve tickets...");
        ReserveTicketResponse response = customerStub.reserveTicket(request);

        if (response.getStatus()) {
            System.out.println("Tickets reserved successfully!");
            System.out.println("Reservation ID: " + response.getReservationId());
        } else {
            System.out.println("Failed to reserve tickets: " + response.getMessage());
        }
    }
}