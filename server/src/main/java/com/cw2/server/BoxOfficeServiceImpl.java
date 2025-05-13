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

public class BoxOfficeServiceImpl extends BoxOfficeServiceGrpc.BoxOfficeServiceImplBase implements DistributedTxListner {
    private ConcertServer server;
    private ManagedChannel channel = null;
    private BoxOfficeServiceGrpc.BoxOfficeServiceBlockingStub clientStub = null;
    private UpdateTicketStockRequest tempDataHolder;
    private boolean transactionStatus = false;

    public BoxOfficeServiceImpl(ConcertServer server) {
        this.server = server;
    }

    @Override
    public void updateTicketStock(UpdateTicketStockRequest request, io.grpc.stub.StreamObserver<UpdateTicketStockResponse> responseObserver) {
        String showId = request.getShowId();
        String seatType = request.getSeatType();
        int additionalTickets = request.getAdditionalTickets();
        int additionalAfterPartyTickets = request.getAdditionalAfterPartyTickets();

        if (server.isLeader()) {
            
            try {
                System.out.println("Updating ticket stock as Primary");
                startDistributedTx(request);
                updateSecondaryServers(request);

                ((DistributedTxCoordinator) server.getTransaction()).perform();
            } catch (Exception e) {
                System.out.println("Error while updating ticket stock: " + e.getMessage());
                e.printStackTrace();
                transactionStatus = false;
            }
        } else {
            
            if (request.getIsSentByPrimary()) {
                System.out.println("Updating ticket stock on secondary, on Primary's command");
                startDistributedTx(request);
                ((DistributedTxParticipant) server.getTransaction()).voteCommit();
            } else {
                UpdateTicketStockResponse response = callPrimary(request);
                transactionStatus = response.getStatus();
            }
        }

        UpdateTicketStockResponse response = UpdateTicketStockResponse.newBuilder()
                .setStatus(transactionStatus)
                .setMessage(transactionStatus ? "Ticket stock updated successfully" : "Failed to update ticket stock")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    
    private void startDistributedTx(UpdateTicketStockRequest request) {
        try {
            server.getTransaction().start("UPDATE_TICKET_STOCK", String.valueOf(UUID.randomUUID()));
            tempDataHolder = request;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGlobalCommit() {
        if (tempDataHolder != null) {
            server.updateTicketStock(
                    tempDataHolder.getShowId(),
                    tempDataHolder.getSeatType(),
                    tempDataHolder.getAdditionalTickets(),
                    tempDataHolder.getAdditionalAfterPartyTickets()
            );
            transactionStatus = true;
            tempDataHolder = null;
        }
    }

    @Override
    public void onGlobalAbort() {
        tempDataHolder = null;
        transactionStatus = false;
        System.out.println("Transaction Aborted by the Coordinator");
    }

    private UpdateTicketStockResponse callPrimary(UpdateTicketStockRequest request) {
        System.out.println("Calling Primary server for updating ticket stock");
        String[] currentLeaderData = server.getCurrentLeaderData();
        String IPAddress = currentLeaderData[0];
        int port = Integer.parseInt(currentLeaderData[1]);

        channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
        clientStub = BoxOfficeServiceGrpc.newBlockingStub(channel);

        return clientStub.updateTicketStock(request);
    }

    private void updateSecondaryServers(UpdateTicketStockRequest request) throws KeeperException, InterruptedException {
        System.out.println("Updating secondary servers for ticket stock update");
        List<String[]> othersData = server.getOthersData();

        for (String[] data : othersData) {
            String IPAddress = data[0];
            int port = Integer.parseInt(data[1]);

            channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
            clientStub = BoxOfficeServiceGrpc.newBlockingStub(channel);

            UpdateTicketStockRequest secondaryRequest = UpdateTicketStockRequest.newBuilder()
                    .setShowId(request.getShowId())
                    .setSeatType(request.getSeatType())
                    .setAdditionalTickets(request.getAdditionalTickets())
                    .setAdditionalAfterPartyTickets(request.getAdditionalAfterPartyTickets())
                    .setIsSentByPrimary(true)
                    .build();

            clientStub.updateTicketStock(secondaryRequest);
        }
    }
}