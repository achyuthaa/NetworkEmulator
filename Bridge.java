import Frames.Dataframe;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Bridge {

    static Scanner sc = new Scanner(System.in);
    static Map<SocketChannel,Integer> connectedClients = new HashMap<>();

    static ArrayList<Integer> connections = new ArrayList<>();
    static HashMap<String,String> station = new HashMap<>();
    static HashMap<String,Integer> SelfLearningtable = new HashMap<>();
    static int numPorts = 0;
    static int connection = 0;
    private static void createSymbolicLink(String target, String link) {
        Path targetPath = Paths.get(target);
        Path linkPath = Paths.get(link);

        try {
            if (Files.isSymbolicLink(linkPath)) {

                Files.delete(linkPath);
            }
            Files.createSymbolicLink(linkPath, targetPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void broadcastMessage(String message) {
        for (Integer client : connections){
            System.out.println(client);
        }

        ByteBuffer messageBuffer = ByteBuffer.wrap(message.getBytes());

        List<SocketChannel> channelsToDisconnect = new ArrayList<>();

        for (SocketChannel client : connectedClients.keySet()) {
            try {
                if (client.isOpen()) {
                    client.write(messageBuffer.duplicate());
                } else {
                    channelsToDisconnect.add(client);
                }
            } catch (IOException e) {
                e.printStackTrace();
                channelsToDisconnect.add(client);
            }
        }
    }


    private static void sendUserMessage(SocketChannel client) throws IOException {
        // Get user input from the command line
        if (sc.hasNextLine()) {
            String userInput = sc.nextLine();

            // Send the user input as a message to the client
            ByteBuffer messageBuffer = ByteBuffer.wrap(userInput.getBytes());
            client.write(messageBuffer);
        }
    }



    private static void clientAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        if (connection >= numPorts) {
            SocketChannel client = serverChannel.accept();
            client.configureBlocking(false);
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            buffer.put("Reject".getBytes());
            buffer.flip();
            client.write(buffer);
            client.close();
            System.out.println("Rejected Connection because of Reaching Max connections");
        } else {
            SocketChannel client = serverChannel.accept();
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


    private static void clientDisconnect(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        int port = connectedClients.get(client);
        connections.remove(Integer.valueOf(port));
        connectedClients.remove(client);
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        connection--;
        System.out.println("Client disconnected from port " + port);
    }
    
    private static void Sltable(){
        for(Map.Entry<String,Integer> entry : SelfLearningtable.entrySet()){
            String key = entry.getKey();
            Integer value = entry.getValue();
            System.out.println("Key:"+ key + ", Value: " + value);
        }
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
        try (FileWriter ipFileWriter = new FileWriter(ipFile)) {
            ipFileWriter.write(sd_address.getHostAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create port file and write port
        File portFile = new File(Portaddress);
        try (FileWriter portFileWriter = new FileWriter(portFile)) {
            portFileWriter.write(String.valueOf(port));
        } catch (IOException e) {
            e.printStackTrace();
        }
        createSymbolicLink(sd_address.getHostAddress(), Ipaddressfilepath);
        createSymbolicLink(String.valueOf(port), Portaddress);

        System.out.println("Server started on IP address " + sd_address.getHostAddress() + " and on port " + port);
        Thread userInputThread = new Thread(() -> {
            while (true) {
                if (sc.hasNextLine()) {
                    String userInput = sc.nextLine();
                    if (userInput.equals("show sltable")) {
                        Sltable();
                    }
                    }
                else if(sc.hasNextLine()) {
                    String userInput = sc.nextLine();
                    broadcastMessage(userInput);
                }

            }
        });

        // Start the user input thread
        userInputThread.start();
        try {
            Selector selector = Selector.open();
            sd_sock.register(selector, SelectionKey.OP_ACCEPT);

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
                                prt = ((InetSocketAddress) client.getRemoteAddress()).getPort();
                                connections.remove(Integer.valueOf(prt));
                                connectedClients.remove(prt);
                                key.cancel();
                                channel.close();
                                System.out.println("closed");
                                continue;
                                //clientDisconnect(key);
                            }
                            buffer.flip();

                            // Deserialize the received object
                            try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(buffer.array()))) {
                                Dataframe receivedData = (Dataframe) objectInputStream.readObject();

                                // Now, you can work with the receivedData object as if it were a Dataframe.
                                String receivedMessage = receivedData.getData();
                                String sourceMacAddress = receivedData.getSourceIpaddress();
                                String destinationMacAddress = receivedData.getDestinationIpaddress();
                                SelfLearningtable.put(sourceMacAddress,prt);

                                // Process the received data as needed
                                        /*byte[] data = new byte[buffer.remaining()];
                                        buffer.get(data);
                                        String response = new String(data);*/
                                System.out.println("Received: " + receivedMessage +"src mac "+sourceMacAddress+" DestMAc"+destinationMacAddress);
                                //key.interestOps(SelectionKey.OP_WRITE);
                            }

                            buffer.clear();
                        }
                        catch (IOException e) {
                            // Handle the connection error gracefully
                            key.cancel();
                            channel.close();
                            System.out.println("Connection error: " + e.getMessage());
                        }
                        catch (ClassNotFoundException e) {
                            // Handle any exceptions here
                            e.printStackTrace();
                        }

                    }
                    if (!key.isValid()) {
                        clientDisconnect(key);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
