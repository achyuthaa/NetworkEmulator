import Frames.ARPframe;
import Frames.Dataframe;
import Frames.Ethernetframe;
import Frames.Ipframe;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class Bridge {

    static Scanner sc = new Scanner(System.in);
    static Map<SocketChannel,Integer> connectedClients = new ConcurrentHashMap<>();

    static ArrayList<Integer> connections = new ArrayList<>();
    static HashMap<String,String> station = new HashMap<>();
    private static final Map<String, Long> ExpirationTimes = new HashMap<>();
    private static final long EXPIRATION_TIME_MS = 30000; // 60 seconds
    static HashMap<String,SocketChannel> SelfLearningtable = new HashMap<>();
    private static final Object lock = new Object();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static int numPorts = 0;
    static int connection = 0;
    private static void broadcastMessage(Ethernetframe frame, SocketChannel senderChannel) throws IOException {
        for (Integer client : connections) {
            System.out.println(client);
        }

        ByteBuffer buffer = ByteBuffer.allocate(10000);

        List<SocketChannel> channelsToDisconnect = new ArrayList<>();

        for (SocketChannel client : connectedClients.keySet()) {
            try {
                if (client.isOpen() && !client.equals(senderChannel)) {
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteStream);
                    objectOutputStream.writeObject(frame);

                    byte[] bytes = byteStream.toByteArray();
                    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

                    client.write(byteBuffer);
                } else if (!client.isOpen()) {
                    channelsToDisconnect.add(client);
                }
            } catch (IOException e) {
                System.out.println("The station is closed! wait for cache to clear or retry");
                e.printStackTrace();
                channelsToDisconnect.add(client);
            }
        }

        // Handle disconnection after the loop to avoid ConcurrentModificationException
        for (SocketChannel disconnectedClient : channelsToDisconnect) {
            connectedClients.remove(disconnectedClient);
        }
    }

    public static void Sendstation(Ethernetframe frame,SocketChannel sd){
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteStream);
            objectOutputStream.writeObject(frame);
            objectOutputStream.flush();

            byte[] bytes = byteStream.toByteArray();
            buffer.put(bytes);
            buffer.flip();

            sd.write(buffer);
        } catch (IOException e) {
            System.out.println("Station is closed! Please wait to clear the cache");
        }
    }



    private static void clientAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel client = serverChannel.accept();
        if (connection >= numPorts) {
            //SocketChannel client = serverChannel.reject();
           //client.configureBlocking(false);
           // ByteBuffer buffer = ByteBuffer.allocate(1024);
           // buffer.put("Reject".getBytes());
          //  buffer.flip();
           // client.write(buffer);
            client.close();
            //key.cancel();
            System.out.println("Rejected Connection because of Reaching Max connections");
        } else {
            connection++;
            InetSocketAddress add = (InetSocketAddress) client.getRemoteAddress();
            String clientIP = add.getAddress().getHostAddress();

            int port = add.getPort();
            connections.add(port);
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
            //client.register(selector,SelectionKey.OP_WRITE);
            connectedClients.put(client, port);
            // Send "Accept" message to the client
            ByteBuffer acceptBuffer = ByteBuffer.wrap("Accept".getBytes());
            client.write(acceptBuffer);

            System.out.println("Accepted connection from: " + client.getRemoteAddress());
            /*for(int i =0;i< connections.size();i++){
                System.out.println(connectedClients.values());
            }*/
        }
    }


    private static void clientDisconnect(SocketChannel client) throws IOException {
        int port = connectedClients.get(client);
        connections.remove(Integer.valueOf(port));
        connectedClients.remove(client);
        try {
            client.close();
        } catch (IOException e) {
            // Log the error or handle it appropriately
            e.printStackTrace();
        }
        connection--;
        System.out.println("Client disconnected from port " + port);
    }

    private static void Sltable(){
        System.out.println("+---------------------+---------------------+-------------------+");
        System.out.println("|    Source MAC      |      Port           | Time Left (seconds)|");
        System.out.println("+---------------------+---------------------+-------------------+");

        for (Map.Entry<String, SocketChannel> entry : SelfLearningtable.entrySet()) {
            String sourceMacAddress = entry.getKey();
            SocketChannel channel = entry.getValue();
            long expirationTime = ExpirationTimes.getOrDefault(sourceMacAddress, 0L);
            long timeLeft = Math.max(0, (expirationTime - System.currentTimeMillis()) / 1000);
            int prt = 0;
            try {
                 prt = ((InetSocketAddress) channel.getRemoteAddress()).getPort();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            System.out.printf("| %-19s | %-19s | %-17s |\n", sourceMacAddress, prt, timeLeft);
        }

        System.out.println("+---------------------+---------------------+-------------------+");
    }

    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);
        if (args.length != 2) {
            System.out.println("The usage is java Bridge lan-name num-ports");
            return;
        }

        numPorts = Integer.parseInt(args[1]);
        String lanName = args[0];
        ServerSocketChannel sd_sock;
        try {
            sd_sock = ServerSocketChannel.open();
            sd_sock.socket().bind(new InetSocketAddress(InetAddress.getLocalHost(), 0), numPorts);
            sd_sock.configureBlocking(false);
        } catch (IOException e) {
            System.out.println("Unable to create socket");
            e.printStackTrace();
            return;
        }

        InetAddress sd_address = ((InetSocketAddress) sd_sock.socket().getLocalSocketAddress()).getAddress();
        int port = sd_sock.socket().getLocalPort();
        String Ipaddressfilepath = "." + lanName + ".addr";
        String Portaddress = "." + lanName + ".port";
        // Create IP address file and write IP address
        File ipFile = new File(Ipaddressfilepath);
        File portFile = new File(Portaddress);
        if (ipFile.exists() || portFile.exists()) {
            System.out.println("Name 'cs1' is already taken. Exiting...");
            System.exit(0);
        }
        try (FileWriter ipFileWriter = new FileWriter(ipFile)) {
            ipFileWriter.write(sd_address.getHostAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create port file and write port
        try (FileWriter portFileWriter = new FileWriter(portFile)) {
            portFileWriter.write(String.valueOf(port));
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Server started on IP address " + sd_address.getHostAddress() + " and on port " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down gracefully...");
            System.out.println("Removing Bridge connection files...");
            if (ipFile.exists()) {
                ipFile.delete();
            }

            if (portFile.exists()) {
                portFile.delete();
            }
            try {
                // Close all connections and release resources
                for (SocketChannel client : connectedClients.keySet()) {
                    clientDisconnect(client);
                }
                // You might need to close other resources if any
                // (e.g., selector, server socket channel, etc.)
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        Thread userInputThread = new Thread(() -> {
            while (true) {
                if (sc.hasNextLine()) {
                    String userInput = sc.nextLine();
                    if (userInput.equals("show sltable")) {
                        Sltable();
                    }
                    else if (userInput.equals("quit")) {
                        System.out.println("Removing all connections...");
                        if (ipFile.exists()) {
                            ipFile.delete();
                        }

                        if (portFile.exists()) {
                            portFile.delete();
                        }
                        for (SocketChannel client : connectedClients.keySet()) {
                            try {
                                clientDisconnect(client);
                                System.out.println("Disconnected client: " + client.getRemoteAddress());
                            } catch (IOException e) {
                                e.printStackTrace();
                                // Handle the exception appropriately, log it, and continue with other clients
                            }
                        }

                        try {
                            sd_sock.close();
                            System.out.println("Server socket closed");
                        } catch (IOException e) {
                            e.printStackTrace();
                            // Handle the exception appropriately
                        }

                        System.out.println("Removed all connections. Exiting server.");
                        System.exit(0);
                    }

                    else {
                        System.out.println("Unrecognized command: " + userInput);
                    }
                    }
            }
        });


        // Start the user input thread
        userInputThread.start();
        try {
            Selector selector = Selector.open();
                if(connection<numPorts) {
                    sd_sock.register(selector, SelectionKey.OP_ACCEPT);
                }

            while (true) {
                int ready = selector.select();
                if (ready == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                        if (key.isAcceptable()) {
                            clientAccept(key, selector);
                        }
                    if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        int prt = ((InetSocketAddress) channel.getRemoteAddress()).getPort();
                        try {
                            int bytesRead = channel.read(buffer);
                            if (bytesRead == -1) {
                                SocketChannel client = (SocketChannel) key.channel();
                                prt = connectedClients.get(channel);
                                connections.remove(Integer.valueOf(prt));
                                connectedClients.remove(prt);
                                connection--;
                                key.cancel();
                                channel.close();
                                System.out.println("closed");
                                continue;
                                //clientDisconnect(key);
                            }
                            buffer.flip();

                            // Deserialize the received object
                            try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(buffer.array()))) {
                                Ethernetframe receivedData = (Ethernetframe) objectInputStream.readObject();
                                Ipframe ipdata = (Ipframe) receivedData.getIframe();
                                    String sourceMacAddress = receivedData.getSourceMacAddress();
                                    String destinationMacAddress = receivedData.getDestinationMacAddress();
                                if (!SelfLearningtable.containsKey(sourceMacAddress)) {
                                    synchronized (lock) {
                                        SelfLearningtable.put(sourceMacAddress, channel);
                                    }
                                    long expirationTime = System.currentTimeMillis() + EXPIRATION_TIME_MS;
                                    ExpirationTimes.put(sourceMacAddress, expirationTime);

                                    scheduler.scheduleAtFixedRate(() -> {
                                        synchronized (lock) {
                                            Iterator<Map.Entry<String, Long>> iterator = ExpirationTimes.entrySet().iterator();
                                            while (iterator.hasNext()) {
                                                Map.Entry<String, Long> entry = iterator.next();
                                                String sourceMacAddres = entry.getKey();
                                                long expirationTim = entry.getValue();

                                                long currentTime = System.currentTimeMillis();
                                                long timeLeft = expirationTim - currentTime;

                                                if (timeLeft <= 0) {
                                                    iterator.remove(); // Remove the entry using iterator to avoid ConcurrentModificationException
                                                    SelfLearningtable.remove(sourceMacAddres);
                                                    System.out.println("Self Learning table entry expired: " + sourceMacAddres);
                                                }
                                            }
                                        }
                                    }, 0, 1000,TimeUnit.MILLISECONDS);
                                }
                                        if (SelfLearningtable.containsKey(destinationMacAddress)) {
                                            synchronized (lock) {
                                                ExpirationTimes.put(destinationMacAddress, System.currentTimeMillis() + EXPIRATION_TIME_MS);
                                            }
                                            synchronized (lock) {
                                                ExpirationTimes.put(sourceMacAddress, System.currentTimeMillis() + EXPIRATION_TIME_MS);
                                            }
                                            SocketChannel sd = null;
                                            synchronized (lock) {
                                                for (Map.Entry<String, SocketChannel> ent : SelfLearningtable.entrySet()) {
                                                    if (ent.getKey().equals(destinationMacAddress)) {
                                                        sd = ent.getValue();
                                                    }
                                                }
                                            }
                                                Sendstation(receivedData, sd);


                                        } else {
                                            synchronized (lock) {
                                                ExpirationTimes.put(sourceMacAddress, System.currentTimeMillis() + EXPIRATION_TIME_MS);
                                            }
                                            broadcastMessage(receivedData,channel);
                                        }
                                    System.out.println("Received: " + " src mac " + sourceMacAddress + " DestMAc " + destinationMacAddress + "  " + receivedData.getIframe());
                            }

                            buffer.clear();
                        }
                        catch (IOException e) {
                            // Handle the connection error gracefully
                            key.cancel();
                            channel.close();
                            System.out.println("Packets are sent all at once! having trouble to deserialize!");
                            e.printStackTrace();
                        }
                        catch (ClassNotFoundException e) {
                            // Handle any exceptions here
                            e.printStackTrace();
                        }

                    }
                    if (!key.isValid()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        clientDisconnect(client);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
