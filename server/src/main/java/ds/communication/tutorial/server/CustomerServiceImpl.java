package ds.communication.tutorial.server;

import ds.tutorial.communication.grpc.generated.*;
import ds.tutorial.synchronization.processs.DistributedTxCoordinator;
import ds.tutorial.synchronization.processs.DistributedTxListner;
import ds.tutorial.synchronization.processs.DistributedTxParticipant;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class CustomerServiceImpl extends CustomerServiceGrpc.CustomerServiceImplBase implements DistributedTxListner {
    private ConcertServer server;
    private ManagedChannel channel = null;
    private CustomerServiceGrpc.CustomerServiceBlockingStub clientStub = null;
    private ReserveTicketRequest tempDataHolder;
    private String reservationId = null;
    private boolean transactionStatus = false;

    public CustomerServiceImpl(ConcertServer server) {
        this.server = server;
    }

    @Override
    public void listConcerts(ListConcertsRequest request, io.grpc.stub.StreamObserver<ListConcertsResponse> responseObserver) {
        System.out.println("Listing all concerts");
        List<ConcertShow> shows = server.getAllConcerts();

        ListConcertsResponse response = ListConcertsResponse.newBuilder()
                .addAllShows(shows)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reserveTicket(ReserveTicketRequest request, io.grpc.stub.StreamObserver<ReserveTicketResponse> responseObserver) {
        String showId = request.getShowId();
        String seatType = request.getSeatType();
        int quantity = request.getQuantity();
        boolean includeAfterParty = request.getIncludeAfterParty();
        String customerId = request.getCustomerId();

        if (server.isLeader()) {
            // Act as primary
            try {
                System.out.println("Reserving tickets as Primary");
                startDistributedTx(request);
                updateSecondaryServers(request);

                ((DistributedTxCoordinator) server.getTransaction()).perform();
            } catch (Exception e) {
                System.out.println("Error while reserving tickets: " + e.getMessage());
                e.printStackTrace();
                transactionStatus = false;
            }
        } else {
            // Act as Secondary
            if (request.getIsSentByPrimary()) {
                System.out.println("Reserving tickets on secondary, on Primary's command");
                startDistributedTx(request);
                ((DistributedTxParticipant) server.getTransaction()).voteCommit();
            } else {
                ReserveTicketResponse response = callPrimary(request);
                transactionStatus = response.getStatus();
                reservationId = response.getReservationId();
            }
        }

        ReserveTicketResponse response = ReserveTicketResponse.newBuilder()
                .setStatus(transactionStatus)
                .setReservationId(reservationId != null ? reservationId : "")
                .setMessage(transactionStatus ? "Tickets reserved successfully" : "Failed to reserve tickets")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        // Reset after response sent
        reservationId = null;
    }

    // Helper methods
    private void startDistributedTx(ReserveTicketRequest request) {
        try {
            server.getTransaction().start("RESERVE_TICKET", String.valueOf(UUID.randomUUID()));
            tempDataHolder = request;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGlobalCommit() {
        if (tempDataHolder != null) {
            reservationId = server.reserveTickets(
                    tempDataHolder.getShowId(),
                    tempDataHolder.getSeatType(),
                    tempDataHolder.getQuantity(),
                    tempDataHolder.getIncludeAfterParty(),
                    tempDataHolder.getCustomerId()
            );

            transactionStatus = (reservationId != null);
            tempDataHolder = null;
        }
    }

    @Override
    public void onGlobalAbort() {
        tempDataHolder = null;
        reservationId = null;
        transactionStatus = false;
        System.out.println("Transaction Aborted by the Coordinator");
    }

    private ReserveTicketResponse callPrimary(ReserveTicketRequest request) {
        System.out.println("Calling Primary server for ticket reservation");
        String[] currentLeaderData = server.getCurrentLeaderData();
        String IPAddress = currentLeaderData[0];
        int port = Integer.parseInt(currentLeaderData[1]);

        channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
        clientStub = CustomerServiceGrpc.newBlockingStub(channel);

        return clientStub.reserveTicket(request);
    }

    private void updateSecondaryServers(ReserveTicketRequest request) throws KeeperException, InterruptedException {
        System.out.println("Updating secondary servers for ticket reservation");
        List<String[]> othersData = server.getOthersData();

        for (String[] data : othersData) {
            String IPAddress = data[0];
            int port = Integer.parseInt(data[1]);

            channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
            clientStub = CustomerServiceGrpc.newBlockingStub(channel);

            ReserveTicketRequest secondaryRequest = ReserveTicketRequest.newBuilder()
                    .setShowId(request.getShowId())
                    .setSeatType(request.getSeatType())
                    .setQuantity(request.getQuantity())
                    .setIncludeAfterParty(request.getIncludeAfterParty())
                    .setCustomerId(request.getCustomerId())
                    .setIsSentByPrimary(true)
                    .build();

            clientStub.reserveTicket(secondaryRequest);
        }
    }
}