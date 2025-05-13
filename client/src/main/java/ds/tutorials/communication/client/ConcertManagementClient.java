package ds.tutorials.communication.client;

import ds.tutorial.communication.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Scanner;

public class ConcertManagementClient {
    private ManagedChannel channel = null;
    ConcertManagementServiceGrpc.ConcertManagementServiceBlockingStub clientStub = null;
    String host = null;
    int port = -1;

    public ConcertManagementClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeConnection() {
        System.out.println("Initializing connection to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        clientStub = ConcertManagementServiceGrpc.newBlockingStub(channel);
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
            System.out.println("6. Update ticket inventory (Box Office)");
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
                    updateTicketInventory(userInput);
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

        System.out.print("Enter concert location: ");
        String location = userInput.nextLine().trim();

        System.out.print("Does this concert have an after-party? (yes/no): ");
        boolean hasAfterParty = userInput.nextLine().trim().equalsIgnoreCase("yes");

        int afterPartyTickets = 0;
        double afterPartyPrice = 0.0;
        if (hasAfterParty) {
            System.out.print("Enter number of after-party tickets available: ");
            afterPartyTickets = Integer.parseInt(userInput.nextLine().trim());

            System.out.print("Enter after-party ticket price: ");
            afterPartyPrice = Double.parseDouble(userInput.nextLine().trim());
        }

        System.out.print("How many seat tiers does this concert have? ");
        int numTiers = Integer.parseInt(userInput.nextLine().trim());

        Concert.Builder concertBuilder = Concert.newBuilder()
                .setConcertId(java.util.UUID.randomUUID().toString())
                .setName(name)
                .setDate(date)
                .setLocation(location)
                .setHasAfterParty(hasAfterParty)
                .setAfterPartyTicketsAvailable(afterPartyTickets)
                .setAfterPartyTicketPrice(afterPartyPrice)
                .setIsCancelled(false);

        for (int i = 0; i < numTiers; i++) {
            System.out.println("\n--- SEAT TIER " + (i+1) + " ---");
            System.out.print("Enter tier name (e.g., Regular, VIP): ");
            String tierName = userInput.nextLine().trim();

            System.out.print("Enter number of available seats: ");
            int availableSeats = Integer.parseInt(userInput.nextLine().trim());

            System.out.print("Enter ticket price for this tier: ");
            double price = Double.parseDouble(userInput.nextLine().trim());

            SeatTier seatTier = SeatTier.newBuilder()
                    .setTierName(tierName)
                    .setAvailableSeats(availableSeats)
                    .setPrice(price)
                    .build();

            concertBuilder.addSeatTiers(seatTier);
        }

        Concert concert = concertBuilder.build();
        AddConcertRequest request = AddConcertRequest.newBuilder()
                .setConcert(concert)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending request to add new concert...");
        AddConcertResponse response = clientStub.addConcert(request);

        if (response.getStatus()) {
            System.out.println("Concert added successfully!");
        } else {
            System.out.println("Failed to add concert: " + response.getMessage());
        }
    }

    private void updateConcertDetails(Scanner userInput) {
        System.out.println("\n--- UPDATE CONCERT DETAILS ---");
        System.out.print("Enter concert ID to update: ");
        String concertId = userInput.nextLine().trim();

        // First fetch the current concert details
        GetConcertRequest getConcertRequest = GetConcertRequest.newBuilder()
                .setConcertId(concertId)
                .build();

        GetConcertResponse getConcertResponse = clientStub.getConcert(getConcertRequest);
        if (getConcertResponse.getConcert() == null || getConcertResponse.getConcert().getConcertId().isEmpty()) {
            System.out.println("Concert not found with ID: " + concertId);
            return;
        }

        Concert oldConcert = getConcertResponse.getConcert();
        Concert.Builder updatedConcertBuilder = oldConcert.toBuilder();

        System.out.println("Current details: " + oldConcert.getName() + " at " + oldConcert.getLocation() + " on " + oldConcert.getDate());

        System.out.print("Enter new name (or press Enter to keep current): ");
        String newName = userInput.nextLine().trim();
        if (!newName.isEmpty()) {
            updatedConcertBuilder.setName(newName);
        }

        System.out.print("Enter new date (YYYY-MM-DD) (or press Enter to keep current): ");
        String newDate = userInput.nextLine().trim();
        if (!newDate.isEmpty()) {
            updatedConcertBuilder.setDate(newDate);
        }

        System.out.print("Enter new location (or press Enter to keep current): ");
        String newLocation = userInput.nextLine().trim();
        if (!newLocation.isEmpty()) {
            updatedConcertBuilder.setLocation(newLocation);
        }

        System.out.print("Update after-party status? (yes/no): ");
        String updateAfterParty = userInput.nextLine().trim();
        if (updateAfterParty.equalsIgnoreCase("yes")) {
            System.out.print("Does this concert have an after-party? (yes/no): ");
            boolean hasAfterParty = userInput.nextLine().trim().equalsIgnoreCase("yes");
            updatedConcertBuilder.setHasAfterParty(hasAfterParty);

            if (hasAfterParty) {
                System.out.print("Enter number of after-party tickets available: ");
                int afterPartyTickets = Integer.parseInt(userInput.nextLine().trim());
                updatedConcertBuilder.setAfterPartyTicketsAvailable(afterPartyTickets);

                System.out.print("Enter after-party ticket price: ");
                double afterPartyPrice = Double.parseDouble(userInput.nextLine().trim());
                updatedConcertBuilder.setAfterPartyTicketPrice(afterPartyPrice);
            } else {
                updatedConcertBuilder.setAfterPartyTicketsAvailable(0);
                updatedConcertBuilder.setAfterPartyTicketPrice(0.0);
            }
        }

        System.out.print("Update seat tiers? (yes/no): ");
        String updateSeatTiers = userInput.nextLine().trim();
        if (updateSeatTiers.equalsIgnoreCase("yes")) {
            updatedConcertBuilder.clearSeatTiers();

            System.out.print("How many seat tiers will this concert have? ");
            int numTiers = Integer.parseInt(userInput.nextLine().trim());

            for (int i = 0; i < numTiers; i++) {
                System.out.println("\n--- SEAT TIER " + (i+1) + " ---");
                System.out.print("Enter tier name (e.g., Regular, VIP): ");
                String tierName = userInput.nextLine().trim();

                System.out.print("Enter number of available seats: ");
                int availableSeats = Integer.parseInt(userInput.nextLine().trim());

                System.out.print("Enter ticket price for this tier: ");
                double price = Double.parseDouble(userInput.nextLine().trim());

                SeatTier seatTier = SeatTier.newBuilder()
                        .setTierName(tierName)
                        .setAvailableSeats(availableSeats)
                        .setPrice(price)
                        .build();

                updatedConcertBuilder.addSeatTiers(seatTier);
            }
        }

        Concert updatedConcert = updatedConcertBuilder.build();
        UpdateConcertRequest request = UpdateConcertRequest.newBuilder()
                .setConcert(updatedConcert)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending request to update concert...");
        UpdateConcertResponse response = clientStub.updateConcert(request);

        if (response.getStatus()) {
            System.out.println("Concert updated successfully!");
        } else {
            System.out.println("Failed to update concert: " + response.getMessage());
        }
    }

    private void cancelConcert(Scanner userInput) {
        System.out.println("\n--- CANCEL CONCERT ---");
        System.out.print("Enter concert ID to cancel: ");
        String concertId = userInput.nextLine().trim();

        System.out.print("Are you sure you want to cancel this concert? (yes/no): ");
        String confirmation = userInput.nextLine().trim();

        if (!confirmation.equalsIgnoreCase("yes")) {
            System.out.println("Cancellation aborted.");
            return;
        }

        CancelConcertRequest request = CancelConcertRequest.newBuilder()
                .setConcertId(concertId)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending request to cancel concert...");
        CancelConcertResponse response = clientStub.cancelConcert(request);

        if (response.getStatus()) {
            System.out.println("Concert cancelled successfully!");
        } else {
            System.out.println("Failed to cancel concert: " + response.getMessage());
        }
    }

    private void listAllConcerts() {
        System.out.println("\n--- LIST ALL CONCERTS ---");

        ListConcertsRequest request = ListConcertsRequest.newBuilder().build();
        ListConcertsResponse response = clientStub.listConcerts(request);

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
        System.out.println("\n--- VIEW CONCERT DETAILS ---");
        System.out.print("Enter concert ID: ");
        String concertId = userInput.nextLine().trim();

        GetConcertRequest request = GetConcertRequest.newBuilder()
                .setConcertId(concertId)
                .build();

        GetConcertResponse response = clientStub.getConcert(request);

        if (response.getConcert() == null || response.getConcert().getConcertId().isEmpty()) {
            System.out.println("Concert not found with ID: " + concertId);
            return;
        }

        Concert concert = response.getConcert();

        System.out.println("\nCONCERT DETAILS");
        System.out.println("====================");
        System.out.println("ID: " + concert.getConcertId());
        System.out.println("Name: " + concert.getName());
        System.out.println("Date: " + concert.getDate());
        System.out.println("Location: " + concert.getLocation());
        System.out.println("Status: " + (concert.getIsCancelled() ? "CANCELLED" : "ACTIVE"));

        System.out.println("\nSEAT TIERS");
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

    private void updateTicketInventory(Scanner userInput) {
        System.out.println("\n--- UPDATE TICKET INVENTORY (BOX OFFICE) ---");
        System.out.print("Enter concert ID: ");
        String concertId = userInput.nextLine().trim();

        System.out.print("Enter seat tier name to update (e.g., Regular, VIP): ");
        String seatTier = userInput.nextLine().trim();

        System.out.print("Enter number of additional seats (can be negative to reduce): ");
        int additionalSeats = Integer.parseInt(userInput.nextLine().trim());

        System.out.print("Enter number of additional after-party tickets (can be negative to reduce): ");
        int additionalAfterPartyTickets = Integer.parseInt(userInput.nextLine().trim());

        UpdateTicketInventoryRequest request = UpdateTicketInventoryRequest.newBuilder()
                .setConcertId(concertId)
                .setSeatTier(seatTier)
                .setAdditionalSeats(additionalSeats)
                .setAdditionalAfterPartyTickets(additionalAfterPartyTickets)
                .setIsSentByPrimary(false)
                .build();

        System.out.println("Sending request to update ticket inventory...");
        UpdateTicketInventoryResponse response = clientStub.updateTicketInventory(request);

        if (response.getStatus()) {
            System.out.println("Ticket inventory updated successfully!");
        } else {
            System.out.println("Failed to update ticket inventory: " + response.getMessage());
        }
    }
}