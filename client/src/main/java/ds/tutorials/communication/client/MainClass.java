package ds.tutorials.communication.client;

/**
 * Main entry point for the Concert Booking System client application.
 * Supports multiple user modes: organizer, customer, box office, and coordinator.
 */
public class MainClass {
    public static void main(String[] args) throws InterruptedException {
        if (args.length != 3) {
            System.out.println("Usage: ConcertBookingClient <host> <port> <mode>");
            System.out.println("  where mode is: organizer(o), customer(c), boxoffice(b), coordinator(e)");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1].trim());
        String mode = args[2].toLowerCase();

        // Standard Java 8 switch statement
        switch (mode) {
            case "o":
            case "organizer":
                // Organizer mode - for adding/updating/cancelling concerts
                ConcertManagementClient organizerClient = new ConcertManagementClient(host, port);
                organizerClient.initializeConnection();
                organizerClient.processUserRequests();
                organizerClient.closeConnection();
                break;

            case "c":
            case "customer":
                // Customer mode - for booking tickets
                TicketBookingClient customerClient = new TicketBookingClient(host, port);
                customerClient.initializeConnection();
                customerClient.processUserRequests();
                customerClient.closeConnection();
                break;

            case "b":
            case "boxoffice":
                // Box office mode - for updating ticket inventory
                ConcertManagementClient boxOfficeClient = new ConcertManagementClient(host, port);
                boxOfficeClient.initializeConnection();

                // Display a simplified box office menu
                System.out.println("\n=============================================");
                System.out.println("CONCERT BOOKING SYSTEM - BOX OFFICE MODE");
                System.out.println("=============================================");
                System.out.println("This mode allows box office clerks to update ticket inventory.");

                boxOfficeClient.processUserRequests();
                boxOfficeClient.closeConnection();
                break;

            case "e":
            case "coordinator":
                // Event coordinator mode - for bulk bookings
                BulkBookingClient coordinatorClient = new BulkBookingClient(host, port);
                coordinatorClient.initializeConnection();
                coordinatorClient.processUserRequests();
                coordinatorClient.closeConnection();
                break;

            default:
                System.out.println("Invalid mode. Please use 'organizer(o)', 'customer(c)', 'boxoffice(b)', or 'coordinator(e)'");
                System.exit(1);
        }
    }
}