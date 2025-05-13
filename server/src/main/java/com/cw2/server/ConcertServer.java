package com.cw2.server;

import ds.tutorial.synchronization.processs.DistributedLock;
import ds.tutorial.synchronization.processs.DistributedTx;
import ds.tutorial.synchronization.processs.DistributedTxCoordinator;
import ds.tutorial.synchronization.processs.DistributedTxParticipant;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import ds.tutorial.communication.grpc.generated.ConcertShow;
import ds.tutorial.communication.grpc.generated.SeatTier;

public class ConcertServer {
    private AtomicBoolean isLeader = new AtomicBoolean(false);
    private byte[] leaderData;
    private DistributedLock leaderLock;
    private int serverPort;

    
    private Map<String, ConcertShow> concerts = new ConcurrentHashMap<>();
    private Map<String, List<String>> reservations = new ConcurrentHashMap<>(); 

    private DistributedTx transaction;
    private ConcertOrganizerServiceImpl concertOrganizerService;
    private BoxOfficeServiceImpl boxOfficeService;
    private CustomerServiceImpl customerService;

    public static void main(String[] args) throws Exception {
        DistributedLock.setZooKeeperURL("localhost:2181");
        DistributedTx.setZooKeeperURL("localhost:2181");
        int serverPort;
        if (args.length != 1) {
            System.out.println("Usage ConcertServer <port>");
            System.exit(1);
        }

        serverPort = Integer.parseInt(args[0].trim());
        ConcertServer server = new ConcertServer("localhost", serverPort);
        server.startServer();
    }

    public ConcertServer(String host, int port) throws InterruptedException, IOException, KeeperException {
        this.serverPort = port;
        leaderLock = new DistributedLock("ConcertServerCluster", buildServerData(host, port));
        concertOrganizerService = new ConcertOrganizerServiceImpl(this);
        boxOfficeService = new BoxOfficeServiceImpl(this);
        customerService = new CustomerServiceImpl(this);
        transaction = new DistributedTxParticipant(concertOrganizerService); 
    }

    public DistributedTx getTransaction() {
        return transaction;
    }

    public void startServer() throws IOException, InterruptedException, KeeperException {
        Server server = ServerBuilder
                .forPort(serverPort)
                .addService(concertOrganizerService)
                .addService(boxOfficeService)
                .addService(customerService)
                .build();
        server.start();
        System.out.println("ConcertServer Started and ready to accept requests on port " + serverPort);
        tryToBeLeader();
        server.awaitTermination();
    }

    private void tryToBeLeader() throws KeeperException, InterruptedException {
        Thread leaderCampaignThread = new Thread(new LeaderCampaignThread());
        leaderCampaignThread.start();
    }

    public static String buildServerData(String IP, int port) {
        StringBuilder builder = new StringBuilder();
        builder.append(IP).append(":").append(port);
        return builder.toString();
    }

    public boolean isLeader() {
        return isLeader.get();
    }

    private synchronized void setCurrentLeaderData(byte[] leaderData) {
        this.leaderData = leaderData;
    }

    class LeaderCampaignThread implements Runnable {
        private byte[] currentLeaderData = null;

        @Override
        public void run() {
            System.out.println("Starting the leader Campaign");
            try {
                boolean leader = leaderLock.tryAcquireLock();
                while (!leader) {
                    byte[] leaderData = leaderLock.getLockHolderData();
                    if (currentLeaderData != leaderData) {
                        currentLeaderData = leaderData;
                        setCurrentLeaderData(currentLeaderData);
                    }
                    Thread.sleep(10000);
                    leader = leaderLock.tryAcquireLock();
                }
                System.out.println("I got the leader lock. Now acting as primary");
                currentLeaderData = null;
                beTheLeader();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    
    public void addConcert(ConcertShow concert) {
        concerts.put(concert.getId(), concert);
        System.out.println("Added concert: " + concert.getName() + " with ID: " + concert.getId());
    }

    public void updateConcert(ConcertShow updatedConcert) {
        concerts.put(updatedConcert.getId(), updatedConcert);
        System.out.println("Updated concert: " + updatedConcert.getName() + " with ID: " + updatedConcert.getId());
    }

    public boolean cancelConcert(String concertId) {
        if (concerts.containsKey(concertId)) {
            concerts.remove(concertId);
            
            reservations.remove(concertId);
            System.out.println("Cancelled concert with ID: " + concertId);
            return true;
        }
        return false;
    }

    public ConcertShow getConcert(String concertId) {
        return concerts.get(concertId);
    }

    public List<ConcertShow> getAllConcerts() {
        return new ArrayList<>(concerts.values());
    }

    
    public synchronized String reserveTickets(String concertId, String seatType, int quantity, boolean includeAfterParty, String customerId) {
        ConcertShow concert = concerts.get(concertId);
        if (concert == null) {
            return null; 
        }

        
        SeatTier targetTier = null;
        int tierIndex = -1;
        for (int i = 0; i < concert.getSeatTiersList().size(); i++) {
            SeatTier tier = concert.getSeatTiersList().get(i);
            if (tier.getType().equals(seatType)) {
                targetTier = tier;
                tierIndex = i;
                break;
            }
        }

        if (targetTier == null || targetTier.getAvailable() < quantity) {
            return null; 
        }

        
        if (includeAfterParty && (concert.getAfterPartyTickets() < quantity || !concert.getHasAfterParty())) {
            return null; 
        }

        
        List<SeatTier> updatedTiers = new ArrayList<>(concert.getSeatTiersList());
        SeatTier updatedTier = SeatTier.newBuilder()
                .setType(targetTier.getType())
                .setPrice(targetTier.getPrice())
                .setAvailable(targetTier.getAvailable() - quantity)
                .build();
        updatedTiers.set(tierIndex, updatedTier);

        
        ConcertShow.Builder updatedConcert = ConcertShow.newBuilder(concert)
                .clearSeatTiers()
                .addAllSeatTiers(updatedTiers);

        if (includeAfterParty) {
            updatedConcert.setAfterPartyTickets(concert.getAfterPartyTickets() - quantity);
        }

        
        concerts.put(concertId, updatedConcert.build());

        
        String reservationId = UUID.randomUUID().toString();
        reservations.computeIfAbsent(concertId, k -> new ArrayList<>()).add(reservationId);

        System.out.println("Reserved " + quantity + " " + seatType + " tickets" +
                (includeAfterParty ? " with after-party" : "") +
                " for concert " + concertId +
                ", reservation ID: " + reservationId);

        return reservationId;
    }

    public synchronized boolean updateTicketStock(String concertId, String seatType, int additionalTickets, int additionalAfterPartyTickets) {
        ConcertShow concert = concerts.get(concertId);
        if (concert == null) {
            return false; 
        }

        ConcertShow.Builder updatedConcert = ConcertShow.newBuilder(concert);

        
        if (seatType != null && !seatType.isEmpty()) {
            List<SeatTier> updatedTiers = new ArrayList<>(concert.getSeatTiersList());
            boolean found = false;

            for (int i = 0; i < updatedTiers.size(); i++) {
                SeatTier tier = updatedTiers.get(i);
                if (tier.getType().equals(seatType)) {
                    SeatTier updatedTier = SeatTier.newBuilder(tier)
                            .setAvailable(tier.getAvailable() + additionalTickets)
                            .build();
                    updatedTiers.set(i, updatedTier);
                    found = true;
                    break;
                }
            }

            
            if (!found && additionalTickets > 0) {
                SeatTier newTier = SeatTier.newBuilder()
                        .setType(seatType)
                        .setPrice(0.0) 
                        .setAvailable(additionalTickets)
                        .build();
                updatedTiers.add(newTier);
            }

            updatedConcert.clearSeatTiers().addAllSeatTiers(updatedTiers);
        }

        
        if (additionalAfterPartyTickets != 0) {
            updatedConcert.setAfterPartyTickets(concert.getAfterPartyTickets() + additionalAfterPartyTickets);

            
            if (additionalAfterPartyTickets > 0 && !concert.getHasAfterParty()) {
                updatedConcert.setHasAfterParty(true);
            }
        }

        
        concerts.put(concertId, updatedConcert.build());

        System.out.println("Updated ticket stock for concert " + concertId +
                (seatType != null ? ": Added " + additionalTickets + " " + seatType + " tickets" : "") +
                (additionalAfterPartyTickets != 0 ? ", Added " + additionalAfterPartyTickets + " after-party tickets" : ""));

        return true;
    }

    public synchronized byte[] getServerState() {
        
        
        return "State serialization placeholder".getBytes();
    }

    public synchronized void loadServerState(byte[] state) {
        
        
        System.out.println("Loading server state");
    }

    public synchronized String[] getCurrentLeaderData() {
        return new String(leaderData).split(":");
    }

    public List<String[]> getOthersData() throws KeeperException, InterruptedException {
        List<String[]> result = new ArrayList<>();
        List<byte[]> othersData = leaderLock.getOthersData();
        for (byte[] data : othersData) {
            String[] dataStrings = new String(data).split(":");
            result.add(dataStrings);
        }
        return result;
    }

    private void beTheLeader() {
        System.out.println("I got the leader lock. Now acting as primary");
        isLeader.set(true);
        transaction = new DistributedTxCoordinator(concertOrganizerService);

        
        try {
            System.out.println("Synchronizing with other nodes as new leader");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}