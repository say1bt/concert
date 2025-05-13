package com.cw2.server;

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
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class CustomerServiceImpl extends CustomerServiceGrpc.CustomerServiceImplBase implements DistributedTxListner {
    private static final Logger logger = Logger.getLogger(CustomerServiceImpl.class.getName());
    private static final int CHANNEL_SHUTDOWN_TIMEOUT_SECONDS = 10;

    private ConcertServer server;
    private ReserveTicketRequest tempDataHolder;
    private String reservationId = null;
    private boolean transactionStatus = false;

    public CustomerServiceImpl(ConcertServer server) {
        this.server = server;
    }

    @Override
    public void listConcerts(ListConcertsRequest request, io.grpc.stub.StreamObserver<ListConcertsResponse> responseObserver) {
        logger.info("Listing all concerts");
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

        try {
            if (server.isLeader()) {
                
                logger.info("Reserving tickets as Primary");
                startDistributedTx(request);
                updateSecondaryServers(request);

                ((DistributedTxCoordinator) server.getTransaction()).perform();
            } else {
                
                if (request.getIsSentByPrimary()) {
                    logger.info("Reserving tickets on secondary, on Primary's command");
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
        } catch (Exception e) {
            logger.severe("Error while reserving tickets: " + e.getMessage());
            e.printStackTrace();

            
            ReserveTicketResponse response = ReserveTicketResponse.newBuilder()
                    .setStatus(false)
                    .setReservationId("")
                    .setMessage("Server error: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } finally {
            
            reservationId = null;
        }
    }

    
    private void startDistributedTx(ReserveTicketRequest request) throws IOException {
        server.getTransaction().start("RESERVE_TICKET", String.valueOf(UUID.randomUUID()));
        tempDataHolder = request;
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
        logger.info("Transaction Aborted by the Coordinator");
    }

    private ReserveTicketResponse callPrimary(ReserveTicketRequest request) {
        logger.info("Calling Primary server for ticket reservation");
        String[] currentLeaderData = server.getCurrentLeaderData();
        String IPAddress = currentLeaderData[0];
        int port = Integer.parseInt(currentLeaderData[1]);

        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress(IPAddress, port)
                    .usePlaintext()
                    .build();

            CustomerServiceGrpc.CustomerServiceBlockingStub clientStub = CustomerServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return clientStub.reserveTicket(request);
        } finally {
            closeChannel(channel, IPAddress, port);
        }
    }

    private void updateSecondaryServers(ReserveTicketRequest request) throws KeeperException, InterruptedException {
        logger.info("Updating secondary servers for ticket reservation");
        List<String[]> othersData = server.getOthersData();

        for (String[] data : othersData) {
            String IPAddress = data[0];
            int port = Integer.parseInt(data[1]);
            ManagedChannel channel = null;

            try {
                channel = ManagedChannelBuilder.forAddress(IPAddress, port)
                        .usePlaintext()
                        .build();

                CustomerServiceGrpc.CustomerServiceBlockingStub clientStub = CustomerServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                ReserveTicketRequest secondaryRequest = ReserveTicketRequest.newBuilder()
                        .setShowId(request.getShowId())
                        .setSeatType(request.getSeatType())
                        .setQuantity(request.getQuantity())
                        .setIncludeAfterParty(request.getIncludeAfterParty())
                        .setCustomerId(request.getCustomerId())
                        .setIsSentByPrimary(true)
                        .build();

                clientStub.reserveTicket(secondaryRequest);
            } finally {
                closeChannel(channel, IPAddress, port);
            }
        }
    }

    
    private void closeChannel(ManagedChannel channel, String ipAddress, int port) {
        if (channel != null) {
            try {
                
                boolean terminated = channel.shutdown()
                        .awaitTermination(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (!terminated) {
                    logger.warning("Channel to " + ipAddress + ":" + port +
                            " did not terminate in " + CHANNEL_SHUTDOWN_TIMEOUT_SECONDS +
                            " seconds. Forcing shutdown.");

                    
                    channel.shutdownNow();

                    
                    if (!channel.awaitTermination(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS/2, TimeUnit.SECONDS)) {
                        logger.severe("Channel to " + ipAddress + ":" + port +
                                " could not be terminated even after forced shutdown.");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.severe("Interrupted while closing channel to " + ipAddress + ":" + port);
                channel.shutdownNow();
            }
        }
    }
}