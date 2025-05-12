package ds.communication.tutorial.server;

import ds.tutorial.communication.grpc.generated.*;
import ds.tutorial.synchronization.processs.DistributedTxCoordinator;
import ds.tutorial.synchronization.processs.DistributedTxListner;
import ds.tutorial.synchronization.processs.DistributedTxParticipant;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javafx.util.Pair;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class ConcertOrganizerServiceImpl extends ConcertOrganizerServiceGrpc.ConcertOrganizerServiceImplBase implements DistributedTxListner {
    private ConcertServer server;
    private ManagedChannel channel = null;
    private ConcertOrganizerServiceGrpc.ConcertOrganizerServiceBlockingStub clientStub = null;
    private Object tempDataHolder;
    private boolean transactionStatus = false;

    public ConcertOrganizerServiceImpl(ConcertServer server) {
        this.server = server;
    }

    @Override
    public void addConcert(AddConcertRequest request, io.grpc.stub.StreamObserver<AddConcertResponse> responseObserver) {
        ConcertShow show = request.getShow();

        if (server.isLeader()) {
            // Act as primary
            try {
                System.out.println("Adding concert as Primary");
                startDistributedTx("ADD_CONCERT", show);
                updateSecondaryServers(request);

                ((DistributedTxCoordinator) server.getTransaction()).perform();
            } catch (Exception e) {
                System.out.println("Error while adding concert: " + e.getMessage());
                e.printStackTrace();
                transactionStatus = false;
            }
        } else {
            // Act as Secondary
            if (request.getIsSentByPrimary()) {
                System.out.println("Adding concert on secondary, on Primary's command");
                startDistributedTx("ADD_CONCERT", show);
                ((DistributedTxParticipant) server.getTransaction()).voteCommit();
            } else {
                AddConcertResponse response = callPrimary(request);
                transactionStatus = response.getStatus();
            }
        }

        AddConcertResponse response = AddConcertResponse.newBuilder()
                .setStatus(transactionStatus)
                .setMessage(transactionStatus ? "Concert added successfully" : "Failed to add concert")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void updateConcert(UpdateConcertRequest request, io.grpc.stub.StreamObserver<UpdateConcertResponse> responseObserver) {
        ConcertShow updatedShow = request.getUpdatedShow();

        if (server.isLeader()) {
            // Act as primary
            try {
                System.out.println("Updating concert as Primary");
                startDistributedTx("UPDATE_CONCERT", updatedShow);
                updateSecondaryServers(request);

                ((DistributedTxCoordinator) server.getTransaction()).perform();
            } catch (Exception e) {
                System.out.println("Error while updating concert: " + e.getMessage());
                e.printStackTrace();
                transactionStatus = false;
            }
        } else {
            // Act as Secondary
            if (request.getIsSentByPrimary()) {
                System.out.println("Updating concert on secondary, on Primary's command");
                startDistributedTx("UPDATE_CONCERT", updatedShow);
                ((DistributedTxParticipant) server.getTransaction()).voteCommit();
            } else {
                UpdateConcertResponse response = callPrimary(request);
                transactionStatus = response.getStatus();
            }
        }

        UpdateConcertResponse response = UpdateConcertResponse.newBuilder()
                .setStatus(transactionStatus)
                .setMessage(transactionStatus ? "Concert updated successfully" : "Failed to update concert")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void cancelConcert(CancelConcertRequest request, io.grpc.stub.StreamObserver<CancelConcertResponse> responseObserver) {
        String showId = request.getShowId();

        if (server.isLeader()) {
            // Act as primary
            try {
                System.out.println("Cancelling concert as Primary");
                startDistributedTx("CANCEL_CONCERT", showId);
                updateSecondaryServers(request);

                ((DistributedTxCoordinator) server.getTransaction()).perform();
            } catch (Exception e) {
                System.out.println("Error while cancelling concert: " + e.getMessage());
                e.printStackTrace();
                transactionStatus = false;
            }
        } else {
            // Act as Secondary
            if (request.getIsSentByPrimary()) {
                System.out.println("Cancelling concert on secondary, on Primary's command");
                startDistributedTx("CANCEL_CONCERT", showId);
                ((DistributedTxParticipant) server.getTransaction()).voteCommit();
            } else {
                CancelConcertResponse response = callPrimary(request);
                transactionStatus = response.getStatus();
            }
        }

        CancelConcertResponse response = CancelConcertResponse.newBuilder()
                .setStatus(transactionStatus)
                .setMessage(transactionStatus ? "Concert cancelled successfully" : "Failed to cancel concert")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // Helper methods
    private void startDistributedTx(String operation, Object data) {
        try {
            server.getTransaction().start(operation, String.valueOf(UUID.randomUUID()));
            tempDataHolder = data;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGlobalCommit() {
        if (tempDataHolder instanceof ConcertShow) {
            if (((ConcertShow) tempDataHolder).getId().isEmpty()) {
                // Handle empty ID case if needed
                return;
            }
            server.addConcert((ConcertShow) tempDataHolder);
        } else if (tempDataHolder instanceof String) {
            server.cancelConcert((String) tempDataHolder);
        }
        transactionStatus = true;
        tempDataHolder = null;
    }

    @Override
    public void onGlobalAbort() {
        tempDataHolder = null;
        transactionStatus = false;
        System.out.println("Transaction Aborted by the Coordinator");
    }

    private AddConcertResponse callPrimary(AddConcertRequest request) {
        System.out.println("Calling Primary server for Add Concert");
        String[] currentLeaderData = server.getCurrentLeaderData();
        String IPAddress = currentLeaderData[0];
        int port = Integer.parseInt(currentLeaderData[1]);

        channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
        clientStub = ConcertOrganizerServiceGrpc.newBlockingStub(channel);

        return clientStub.addConcert(request);
    }

    private UpdateConcertResponse callPrimary(UpdateConcertRequest request) {
        System.out.println("Calling Primary server for Update Concert");
        String[] currentLeaderData = server.getCurrentLeaderData();
        String IPAddress = currentLeaderData[0];
        int port = Integer.parseInt(currentLeaderData[1]);

        channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
        clientStub = ConcertOrganizerServiceGrpc.newBlockingStub(channel);

        return clientStub.updateConcert(request);
    }

    private CancelConcertResponse callPrimary(CancelConcertRequest request) {
        System.out.println("Calling Primary server for Cancel Concert");
        String[] currentLeaderData = server.getCurrentLeaderData();
        String IPAddress = currentLeaderData[0];
        int port = Integer.parseInt(currentLeaderData[1]);

        channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
        clientStub = ConcertOrganizerServiceGrpc.newBlockingStub(channel);

        return clientStub.cancelConcert(request);
    }

    private void updateSecondaryServers(AddConcertRequest request) throws KeeperException, InterruptedException {
        System.out.println("Updating secondary servers for Add Concert");
        List<String[]> othersData = server.getOthersData();

        for (String[] data : othersData) {
            String IPAddress = data[0];
            int port = Integer.parseInt(data[1]);

            channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
            clientStub = ConcertOrganizerServiceGrpc.newBlockingStub(channel);

            AddConcertRequest secondaryRequest = AddConcertRequest.newBuilder()
                    .setShow(request.getShow())
                    .setIsSentByPrimary(true)
                    .build();

            clientStub.addConcert(secondaryRequest);
        }
    }

    private void updateSecondaryServers(UpdateConcertRequest request) throws KeeperException, InterruptedException {
        System.out.println("Updating secondary servers for Update Concert");
        List<String[]> othersData = server.getOthersData();

        for (String[] data : othersData) {
            String IPAddress = data[0];
            int port = Integer.parseInt(data[1]);

            channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
            clientStub = ConcertOrganizerServiceGrpc.newBlockingStub(channel);

            UpdateConcertRequest secondaryRequest = UpdateConcertRequest.newBuilder()
                    .setShowId(request.getShowId())
                    .setUpdatedShow(request.getUpdatedShow())
                    .setIsSentByPrimary(true)
                    .build();

            clientStub.updateConcert(secondaryRequest);
        }
    }

    private void updateSecondaryServers(CancelConcertRequest request) throws KeeperException, InterruptedException {
        System.out.println("Updating secondary servers for Cancel Concert");
        List<String[]> othersData = server.getOthersData();

        for (String[] data : othersData) {
            String IPAddress = data[0];
            int port = Integer.parseInt(data[1]);

            channel = ManagedChannelBuilder.forAddress(IPAddress, port).usePlaintext().build();
            clientStub = ConcertOrganizerServiceGrpc.newBlockingStub(channel);

            CancelConcertRequest secondaryRequest = CancelConcertRequest.newBuilder()
                    .setShowId(request.getShowId())
                    .setIsSentByPrimary(true)
                    .build();

            clientStub.cancelConcert(secondaryRequest);
        }
    }
}