import Frames.*;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class client {
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static boolean connectionEstablished = false;
    private static final int CONNECTION_INTERVAL_SECONDS = 2;
    static Selector selector;
    static Map<String, String[]> iptable = new ConcurrentHashMap<>();
    static List<SocketChannel> connections = new ArrayList<>();
    static Map<String, String> Hosts = new ConcurrentHashMap<>();
    static Map<String, String[]> rtable = new ConcurrentHashMap<>();
    static Map<String, String> Arpcache = new ConcurrentHashMap<>();
    static Map<String, SocketChannel> path = new ConcurrentHashMap<>();
    private static final Map<String, Long> ExpirationTimes = new HashMap<>();
    private static final long EXPIRATION_TIME_MS = 30000; // 60 seconds
    private static final Object lock = new Object();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    static Map<String, Integer> serverss = new HashMap<>();
    static String ResultIP = "";
    static String srcinterface = "";
    static String sourceip = "";
    static String Sourcemac = "";

    static Queue<PacketQ> packetqueue = new LinkedList<>();

    public static void exitGracefully() {
        System.out.println("Closing connections gracefully...");

        for (SocketChannel channel : connections) {
            try {
                channel.close();
            } catch (IOException e) {
                e.printStackTrace(); // Handle the exception according to your needs
            }
        }

        System.out.println("Exiting program.");
        System.exit(0);
    }

    public static void Sendobject(Ethernetframe frame,SocketChannel sd) throws IOException {
        try {
            if (frame.getIframe() == null) {
                System.out.println("Error: Attempting to send a null frame.");
                return;
            }
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
            sd.close();
            e.printStackTrace();
        }
    }

    public static void Getrtables(String rtab) {
        String filePath = "RoutingTables/" + rtab;

        // Create a map to store the rtable data

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Reading line: " + line); // Debugging line

                // Split the line into columns based on whitespace
                String[] columns = line.trim().split("\\s+");

                //System.out.println("Number of columns: " + columns.length);

                // Check if the line has the expected number of columns
                if (columns.length >= 4) {
                    // Extract relevant information
                    String destIP = columns[0];
                    String nextHop = columns[1];
                    String netmask = columns[2];
                    String bridgename = columns[3];

                    // Store the information in the map
                    rtable.put(destIP, new String[]{nextHop, netmask, bridgename});
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void Getiface(String inter) {
        String filePath = "Interfaces/" + inter;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Split the line into columns based on whitespace
                String[] columns = line.trim().split("\\s+");

                // Check if the line has the expected number of columns
                if (columns.length >= 5) {
                    // Extract relevant information
                    String iface = columns[0];
                    String ipAddress = columns[1];
                    String netmask = columns[2];
                    String macAddress = columns[3];
                    String description = columns[4];

                    // Store the information in the map
                    iptable.put(iface, new String[]{ipAddress, netmask, macAddress, description});
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void Gethosts(String host) {
        String hostpath = "Hosts/" + host;
        try (Stream<String> lines = Files.lines(Paths.get(hostpath))) {
            lines.forEach(line -> {
                String[] parts = line.split("\t");
                if (parts.length == 2) {
                    String hostName = parts[0].trim();
                    String ipAddress = parts[1].trim();
                    Hosts.put(hostName, ipAddress);
                } else {
                    // Attempt to handle lines with different formatting
                    Pattern pattern = Pattern.compile("^(\\S+)\\s+(\\S+)$");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String hostName = matcher.group(1).trim();
                        String ipAddress = matcher.group(2).trim();
                        Hosts.put(hostName, ipAddress);
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        //Host to IpMapping
        String inter = args[1];
        String rtab = args[2];
        String host = args[3];

        Gethosts(host);
        Getiface(inter);
        Getrtables(rtab);
        Scanner sc = new Scanner(System.in);
        String serverIP = null;
        int serverPort = 0;
        selector = Selector.open();

        for (Map.Entry<String, String[]> entry : iptable.entrySet()) {
            String ifaces = entry.getKey();
            String[] valuess = entry.getValue();
            serverIP = null;
            try {
                serverIP = readSymbolicLink("." + valuess[3] + ".addr");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            serverPort = 0;
            try {
                serverPort = Integer.parseInt(readSymbolicLink("." + valuess[3] + ".port"));
                serverss.put(serverIP, serverPort);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);

            SocketAddress serverAddress = new InetSocketAddress(InetAddress.getByName(serverIP), serverPort);
            socketChannel.connect(serverAddress);
            connections.add(socketChannel);
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            // Add the SocketChannel to the path map
            path.put(valuess[3], socketChannel);
        }

        // Create a thread to handle user input
        Thread userInputThread = new Thread(() -> {
            while (true) {
                if (sc.hasNextLine()) {
                    String userInput = sc.nextLine();
                    String[] parts = userInput.split(" ");
                    if (userInput.equals("show hosts")) {
                        // Display the stored information in a table-like format
                        System.out.println("+-------------------+-------------------+");
                        System.out.println("|     Host Name    |     IP Address    |");
                        System.out.println("+-------------------+-------------------+");

                        for (Map.Entry<String, String> entry : Hosts.entrySet()) {
                            System.out.printf("| %-17s | %-17s |\n", entry.getKey(), entry.getValue());
                        }

                        System.out.println("+-------------------+-------------------+");
                    } else if (userInput.equals("show iface")) {


                        // Display the iptable as a table
                        System.out.println("+-------------------+-------------------+-------------------+-------------------+-------------------+");
                        System.out.println("|      Interface   |     IP Address    |      Netmask      |     MAC Address   |    Bridge Name    |");
                        System.out.println("+-------------------+-------------------+-------------------+-------------------+-------------------+");

                        for (Map.Entry<String, String[]> entry : iptable.entrySet()) {
                            String iface = entry.getKey();
                            String[] values = entry.getValue();

                            System.out.printf("| %-17s | %-17s | %-17s | %-17s | %-17s |\n", iface, values[0], values[1], values[2], values[3]);
                        }

                        System.out.println("+-------------------+-------------------+-------------------+-------------------+-------------------+");
                    } else if (userInput.equals("show rtable")) {

                        System.out.println("+-------------------+-------------------+-------------------+-------------------+");
                        System.out.println("|   Destination IP |      Next Hop     |     Netmask       |    Bridge Name    |");
                        System.out.println("+-------------------+-------------------+-------------------+-------------------+");

                        for (Map.Entry<String, String[]> entry : rtable.entrySet()) {
                            String destIP = entry.getKey();
                            String[] values = entry.getValue();

                            System.out.printf("| %-17s | %-17s | %-17s | %-17s |\n", destIP, values[0], values[1], values[2]);
                        }

                        System.out.println("+-------------------+-------------------+-------------------+-------------------+");

                    }  else if (userInput.equals("show arp")) {
                        System.out.println("+-------------------+-------------------+-------------------+");
                        System.out.println("|   IP Address     |     MAC Address   |    Time Left (s)   |");
                        System.out.println("+-------------------+-------------------+-------------------+");

                        for (Map.Entry<String, String> entry : Arpcache.entrySet()) {
                            String ipAddress = entry.getKey();
                            String macAddress = entry.getValue();
                            /*for (Map.Entry<String,Long> ok: ExpirationTimes.entrySet()
                                 ) {System.out.println("ip"+ok.getKey()+"mac"+ok.getValue());

                            }*/

                            long expirationTime = ExpirationTimes.getOrDefault(macAddress, 0L);
                            long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(expirationTime - System.currentTimeMillis()));

                            System.out.printf("| %-17s | %-17s | %-17s |\n", ipAddress, macAddress, timeLeft);
                        }

                        System.out.println("+-------------------+-------------------+-------------------+");
                    }
                    else if (userInput.equals("show pq")) {
                        System.out.println("+------------------+");
                        System.out.println("| Packets in Queue |");
                        System.out.println("+------------------+");

                        int packetCount = packetqueue.size();

                        System.out.printf("| %-16d |\n", packetCount);

                        System.out.println("+------------------+");
                        for (PacketQ Q: packetqueue
                        ) {
                            System.out.println(Q.getIframe().getDframe().getData());
                        }
                    }
                    else if (userInput.equals("quit")) {
                        System.out.println("Removing all connections gracefully and exiting...");

                        exitGracefully();

                        System.exit(0);
                    }
                    else  if (parts.length >= 3 && "send".equals(parts[0])) {
                            if(args[0].equals("-no")) {
                            String recipient = parts[1];
                            String message = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
                            String Destinationip = Hosts.get(recipient);
                            //System.out.println("Destinatip check" + " " + Destinationip);
                            sourceip = "";
                            String nexthop = null;
                            String tnexthop = null;
                            Sourcemac = null;
                            String Destinationmac = null;
                            for (Map.Entry<String, String[]> entry : rtable.entrySet()) {
                                tnexthop = entry.getKey();
                                String values[] = entry.getValue();
                                String subnetmask = values[1];
                                //System.out.println(subnetmask);

                                String[] octets1 = Destinationip.split("\\.");
                                String[] octets2 = subnetmask.split("\\.");
                                int resultOctets[] = new int[4];
                                for (int i = 0; i < 4; i++) {
                                    int octet1 = Integer.parseInt(octets1[i]);
                                    int octet2 = Integer.parseInt(octets2[i]);
                                    resultOctets[i] = octet1 & octet2;
                                    ResultIP = String.format("%d.%d.%d.%d", resultOctets[0], resultOctets[1], resultOctets[2], resultOctets[3]);

                                    if (ResultIP.equals(tnexthop)) {
                                        sourceip = Hosts.get(values[2]);
                                        srcinterface = values[2];
                                        nexthop = values[0];
                                        //System.out.println(ResultIP);
                                        //System.out.println(sourceip);
                                        break;
                                    }
                                }
                            }
                            //System.out.println("nexthopcheck" + " " + nexthop);

                            Dataframe data = new Dataframe.Builder().Data(message).build();
                            Ipframe pack = new Ipframe.Builder().SourceIpaddress(sourceip).dframe(data).destinationIP(Destinationip).build();
                            String[] arr = iptable.get(srcinterface);
                            Sourcemac = arr[2];
                            String bri = arr[3];
                            String nexthopip = null;
                            if (nexthop.equals("0.0.0.0")) {
                                nexthopip = Destinationip;
                            } else {
                                nexthopip = nexthop;
                            }
                            Ipframe arppack = null;
                            Ethernetframe Arpreq = null;
                            SocketChannel sd = path.get(bri);
                            if (!Arpcache.containsKey(nexthopip)) {
                                PacketQ Qpack = new PacketQ.Builder().iframe(pack).nextHop(nexthopip).build();
                                packetqueue.add(Qpack);
                                System.out.println("The arpcache doesnot have the info, sending arprequest!");
                                System.out.println("Packet added to Pending Queue, ARP Request Sent");
                                arppack = new ARPframe.Builder("1").Sourcemac(Sourcemac).SourceIpaddress(sourceip).destinationIP(nexthopip).build();
                                Arpreq = new Ethernetframe.Builder().getType(1).SourceMacAddress(Sourcemac).ipframe(arppack).build();
                                try {
                                    Sendobject(Arpreq, sd);
                                } catch (IOException e) {
                                    System.out.println("connection closed");
                                }
                            } else {
                                String Dmac = Arpcache.get(nexthopip);
                                //System.out.println("going here");
                                //Iterator<PacketQ> iterator = packetqueue.iterator();
                                arppack = pack;
                                Arpreq = new Ethernetframe.Builder()
                                        .getType(1)
                                        .SourceMacAddress(Sourcemac)
                                        .DestinationMacAddress(Dmac)
                                        .ipframe(arppack)
                                        .build();

                                System.out.println("The info exists in the arpcache, and the packet is sent to the next hop");

                                /*while (iterator.hasNext()) {
                                    PacketQ packetout = iterator.next();

                                    if (packetout.getNextHop().equals(nexthopip)) {
                                        for (Map.Entry<String, String> ent : Arpcache.entrySet()) {
                                            if (ent.getKey().equals(packetout.getNextHop())) {
                                                Dmac = ent.getValue();
                                                iterator.remove(); // Safely remove the current element from the collection

                                                arppack = packetout.getIframe();
                                                Arpreq = new Ethernetframe.Builder()
                                                        .getType(1)
                                                        .SourceMacAddress(Sourcemac)
                                                        .DestinationMacAddress(Dmac)
                                                        .ipframe(arppack)
                                                        .build();

                                                System.out.println("The info exists in the arpcache, and the packet is sent to the next hop");
                                                //System.out.println("Q sent");

                                                try {
                                                    Sendobject(Arpreq, sd);
                                                } catch (IOException e) {
                                                    System.out.println("Connection closed");
                                                }
                                            }
                                        }
                                    }
                                } */
                                try {
                                    Sendobject(Arpreq, sd);
                                } catch (IOException e) {
                                    System.out.println("Connection closed");
                                }

                                synchronized (lock) {
                                    ExpirationTimes.put(Dmac, System.currentTimeMillis() + EXPIRATION_TIME_MS);
                                }
                            }
                        }
                        else{
                            System.out.println("This is a router you cannot send here");
                        }
                    }
                    else {
                        System.out.println("Unrecognized command: " + userInput);
                    }
                }
            }

        });

        // Start the user input thread
        userInputThread.start();
        int connectionAttempts = 0;
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        while (true) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                if (key.isConnectable()) {
                    SocketChannel channel = (SocketChannel) key.channel();
                    if (channel.isConnectionPending()) {
                        channel.finishConnect();
                    }
                    channel.register(selector, SelectionKey.OP_READ);
                } else if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(10000);
                    int bytesRead = channel.read(buffer);
                    if (bytesRead == -1) {
                        key.cancel();
                        channel.close();
                        connections.remove(channel);
                        return;
                    }
                    buffer.flip();

                    if (StandardCharsets.UTF_8.decode(buffer).toString().equals("Accept")) {
                        System.out.println("Accept");
                        continue;
                    }

                    try {
                        ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(buffer.array()));
                        Object receivedObject = objectInputStream.readObject();
                        //System.out.println(receivedObject);

                        if (receivedObject instanceof Ethernetframe) {
                            Ethernetframe receivedData = (Ethernetframe) receivedObject;
                            Ipframe receivedObjec = receivedData.getIframe();
                            String Arptype = "";
                            if(receivedObjec instanceof ARPframe) {
                                ARPframe receivedarp = (ARPframe) receivedObjec;
                                Arptype = receivedarp.getArptype();


                                String arpsrc = receivedData.getSourceMacAddress();

                                String arpsip = receivedarp.getSourceIP();
                                String arpdip = receivedarp.getDestinationIp();

                                //Arpcache.put(arpsip,arpsrc);

                                if (Arptype.equals("1")) {
                                    System.out.println("Received Arprequest");
                                    String sourceMacAddress = receivedData.getSourceMacAddress();
                                    String destinationMacAddress = receivedData.getDestinationMacAddress();
                                    //System.out.println("Received Ethernetframe: src MAC " + sourceMacAddress + ", Dest MAC " + destinationMacAddress + " ArpType: " + receivedData.getType());
                                    for (Map.Entry<String, String[]> entry : iptable.entrySet()) {
                                        String values[] = entry.getValue();
                                        if (values[0].equals(arpdip)) {
                                            Sourcemac = values[2];
                                            sourceip = values[0];
                                        }


                                    }
                                    synchronized (lock) {
                                        Arpcache.put(arpsip,arpsrc);
                                    }
                                    long expirationTime = System.currentTimeMillis() + EXPIRATION_TIME_MS;
                                    synchronized (lock) {
                                        ExpirationTimes.put(arpsrc, expirationTime);
                                    }

                                    scheduler.scheduleAtFixedRate(() -> {
                                        synchronized (lock) {
                                            Iterator<Map.Entry<String, Long>> iterator = ExpirationTimes.entrySet().iterator();
                                            while (iterator.hasNext()) {
                                                Map.Entry<String, Long> entry = iterator.next();
                                                String sourceip = entry.getKey();
                                                long expirationTim = entry.getValue();

                                                long currentTime = System.currentTimeMillis();
                                                long timeLeft = expirationTim - currentTime;

                                                if (timeLeft <= 0) {
                                                    String ip = null;
                                                    for (Map.Entry<String,String> ok: Arpcache.entrySet()
                                                    ) {
                                                        if(ok.getValue().equals(sourceip)){
                                                            ip = ok.getKey();
                                                        }
                                                    }
                                                    Arpcache.remove(ip);
                                                    iterator.remove(); // Remove the entry using iterator to avoid ConcurrentModificationException
                                                    System.out.println("Arpcache entry expired: " + sourceip);
                                                }
                                            }
                                        }
                                    }, 0, 1000,TimeUnit.MILLISECONDS);
                                    //System.out.println("reachedthis " + Sourcemac + " " + sourceip + " " + receivedarp.getDestinationIp());
                                    if (receivedarp.getDestinationIp().equals(sourceip)) {
                                        System.out.println("Sent Arpresponse");
                                        Ipframe backarp = new ARPframe.Builder("2").Sourcemac(arpsrc).Destinationmac(Sourcemac).destinationIP(arpsip).SourceIpaddress(arpdip).build();
                                        //System.out.println(backarp);
                                        Ethernetframe frame = new Ethernetframe.Builder().getType(8).SourceMacAddress(Sourcemac).DestinationMacAddress(receivedData.getSourceMacAddress()).ipframe(backarp).build();
                                        Sendobject(frame, channel);
                                    }
                                    else{
                                        System.out.println("Not matching, arp response not sent!");
                                    }

                                }


                                if (Arptype.equals("2")) {
                                    System.out.println("Received Arpresponse");
                                    String destinmac = receivedarp.getDestinationmac();
                                    String destip = receivedarp.getSourceIP();
                                    synchronized (lock) {
                                        Arpcache.put(arpsip,arpsrc);
                                    }
                                    long expirationTime = System.currentTimeMillis() + EXPIRATION_TIME_MS;
                                    ExpirationTimes.put(arpsrc, expirationTime);

                                    scheduler.scheduleAtFixedRate(() -> {
                                        synchronized (lock) {
                                            Iterator<Map.Entry<String, Long>> iterator = ExpirationTimes.entrySet().iterator();
                                            while (iterator.hasNext()) {
                                                Map.Entry<String, Long> entry = iterator.next();
                                                String sourceip = entry.getKey();
                                                long expirationTim = entry.getValue();

                                                long currentTime = System.currentTimeMillis();
                                                long timeLeft = expirationTim - currentTime;

                                                if (timeLeft <= 0) {
                                                    String ip = null;
                                                    iterator.remove(); // Remove the entry using iterator to avoid ConcurrentModificationException
                                                    for (Map.Entry<String,String> ok: Arpcache.entrySet()
                                                    ) {
                                                        if(ok.getValue().equals(sourceip)){
                                                            ip = ok.getKey();
                                                        }
                                                    }
                                                    Arpcache.remove(ip);
                                                    System.out.println("Arpcache entry expired: " + sourceip);
                                                }
                                            }
                                        }
                                    }, 0, 1000,TimeUnit.MILLISECONDS);
                                    Iterator<PacketQ> iterator = packetqueue.iterator();
                                    while (iterator.hasNext()) {
                                        PacketQ packet = iterator.next();
                                        //System.out.println(packet.getNextHop()+" "+destip);
                                        if (packet.getNextHop().equals(destip)) {
                                            Ipframe packtosend = packet.getIframe();
                                            Ethernetframe finalframe = new Ethernetframe.Builder()
                                                    .getType(1)
                                                    .SourceMacAddress(Sourcemac)
                                                    .ipframe(packtosend)
                                                    .DestinationMacAddress(destinmac)
                                                    .build();
                                            System.out.println("The packet is now sent");
                                            try {
                                                Thread.sleep(2000);
                                            } catch (InterruptedException e) {
                                                throw new RuntimeException(e);
                                            }
                                            Sendobject(finalframe, channel);
                                            iterator.remove(); // Safely remove the current element from the collection
                                        }
                                    }

                                }
                            }
                            else if(receivedObjec.getDestinationIp().equals(sourceip)){
                                long expirationTime = System.currentTimeMillis() + EXPIRATION_TIME_MS;
                                ExpirationTimes.put(((Ethernetframe) receivedObject).getSourceMacAddress(), expirationTime);
                                String Hostname = null;
                                for (Map.Entry<String,String> hostt: Hosts.entrySet()
                                     ) {
                                    String Key = hostt.getKey();
                                    String Value = hostt.getValue();
                                    if(Value.equals(receivedObjec.getSourceIP())){
                                        Hostname = Key;
                                    }
                                }
                                System.out.println("Received message from "+Hostname+" is >> "+receivedObjec.getDframe().getData() );
                            }
                            else{
                                if(args[0].equals("-route")) {
                                    synchronized (lock) {
                                        ExpirationTimes.put(((Ethernetframe) receivedObject).getSourceMacAddress(), System.currentTimeMillis() + EXPIRATION_TIME_MS);
                                    }
                                    String nexthop = null;
                                    String tnexthop = null;
                                    Sourcemac = null;
                                    String Destinationmac = null;
                                    for (Map.Entry<String, String[]> entry : rtable.entrySet()) {
                                        tnexthop = entry.getKey();
                                        String values[] = entry.getValue();
                                        String subnetmask = values[1];
                                        //System.out.println(subnetmask);

                                        String[] octets1 = receivedObjec.getDestinationIp().split("\\.");
                                        String[] octets2 = subnetmask.split("\\.");
                                        int resultOctets[] = new int[4];
                                        for (int i = 0; i < 4; i++) {
                                            int octet1 = Integer.parseInt(octets1[i]);
                                            int octet2 = Integer.parseInt(octets2[i]);
                                            resultOctets[i] = octet1 & octet2;
                                            ResultIP = String.format("%d.%d.%d.%d", resultOctets[0], resultOctets[1], resultOctets[2], resultOctets[3]);

                                            if (ResultIP.equals(tnexthop)) {
                                                sourceip = Hosts.get(values[2]);
                                                srcinterface = values[2];
                                                nexthop = values[0];
                                                //System.out.println(ResultIP);
                                                //System.out.println(sourceip);
                                                break;
                                            }
                                        }
                                    }
                                    //System.out.println("nexthopcheck" + " " + nexthop);

                                    String[] arr = iptable.get(srcinterface);
                                    Sourcemac = arr[2];
                                    String bri = arr[3];
                                    String nexthopip = null;
                                    if (nexthop.equals("0.0.0.0")) {
                                        nexthopip = receivedObjec.getDestinationIp();
                                    } else {
                                        nexthopip = nexthop;
                                    }
                                    Ipframe arppack = null;
                                    Ethernetframe Arpreq = null;
                                    SocketChannel sd = path.get(bri);
                                    if (!Arpcache.containsKey(nexthopip)) {
                                        System.out.println("The Router doesn't have the info, arp request is sent!");
                                        PacketQ Qpack = new PacketQ.Builder().iframe(receivedObjec).nextHop(nexthopip).build();
                                        packetqueue.add(Qpack);
                                        arppack = new ARPframe.Builder("1").Sourcemac(Sourcemac).SourceIpaddress(sourceip).destinationIP(nexthopip).build();
                                        Arpreq = new Ethernetframe.Builder().getType(1).SourceMacAddress(Sourcemac).ipframe(arppack).build();
                                        Sendobject(Arpreq, sd);
                                    } else {
                                        String Dmac = null;
                                        PacketQ packtosend = null;
                                        //SocketChannel sd = path.get(bri);
                                  /*  //Arpreq =  new Ethernetframe.Builder().getType(1).SourceMacAddress(Sourcemac).DestinationMacAddress(Dmac).ipframe(receivedObjec).build();
                                    System.out.println("original packet is sent");
                                    SocketChannel sd = path.get(bri);
                                    for (PacketQ Qpac : packetqueue
                                    ) {
                                        if (Qpac.getNextHop().equals(nexthopip)) {
                                            packtosend = packetqueue.poll();
                                            for (Map.Entry<String, String> ent : Arpcache.entrySet()) {
                                                if (ent.getKey().equals(receivedObjec.getDestinationIp())) {
                                                    Dmac = ent.getValue();
                                                }
                                            }
                                            Ipframe packet = packtosend.getIframe();
                                            Ethernetframe packready = new Ethernetframe.Builder().getType(0).DestinationMacAddress(Dmac).SourceMacAddress(Sourcemac).ipframe(packet).build();
                                            //SocketChannel sd = path.get(bri);
                                            Sendobject(packready, sd);
                                        }
                                    }
                                    //System.out.println("going here");
                                /*for (PacketQ packetout: packetqueue) {
                                    if(packetout.getNextHop().equals(pack.getDestinationIp())){
                                        PacketQ packetpop = packetqueue.poll();
                                        arppack = packetout.getIframe();
                                        String Dmac = null;
                                        for ( Map.Entry<String,String> ent: Arpcache.entrySet()) {
                                            if(ent.getKey().equals(arppack.getDestinationIp())){
                                                Dmac = ent.getValue();
                                            }
                                        }

                                    }
                                }*/
                                        for (Map.Entry<String, String> ent : Arpcache.entrySet()) {
                                            if (ent.getKey().equals(nexthopip)) {
                                                Dmac = ent.getValue();
                                            }
                                        }
                                        synchronized (lock) {
                                            ExpirationTimes.put(Dmac, System.currentTimeMillis() + EXPIRATION_TIME_MS);
                                        }
                                        Arpreq = new Ethernetframe.Builder().getType(1).SourceMacAddress(Sourcemac).DestinationMacAddress(Dmac).ipframe(receivedObjec).build();
                                        System.out.println("The Router received the Packet!");
                                        System.out.println("The Router contains the info, packet directly sent!");
                                        //System.out.println("original packet is sent");
                                        Sendobject(Arpreq, sd);

                                        /*for (PacketQ packetout : packetqueue) {
                                            if (packetout.getNextHop().equals(receivedObjec.getDestinationIp())) {
                                                PacketQ packetpop = packetqueue.poll();
                                                arppack = packetout.getIframe();
                                                //String Dmac = null;
                                                for (Map.Entry<String, String> ent : Arpcache.entrySet()) {
                                                    if (ent.getKey().equals(arppack.getDestinationIp())) {
                                                        Dmac = ent.getValue();
                                                    }
                                                    Arpreq = new Ethernetframe.Builder().getType(1).SourceMacAddress(Sourcemac).DestinationMacAddress(Dmac).ipframe(arppack).build();
                                                    Sendobject(Arpreq, sd);
                                                }


                                            }
                                        }
                                        */
                                    }

                                }

                            }
                            //System.out.println("Received Ethernetframe: src MAC " + sourceMacAddress + ", Dest MAC " + destinationMacAddress + " ArpType: " + receivedData.getType());
                        } else if (receivedObject instanceof String) {
                            String receivedString = (String) receivedObject;
                            System.out.println("Received String: " + receivedString);
                        } else if (receivedObject instanceof ARPframe) {

                        } else {
                            System.out.println("Received an object of type: " + receivedObject.getClass().getName());
                        }

                    } catch (IOException | ClassNotFoundException e) {
                        //key.cancel();
                        //channel.close();
                        System.out.println("Connection error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static String readSymbolicLink(String linkName) throws IOException {
        File symbolicLinkFile = new File(linkName);
        if (symbolicLinkFile.exists() && symbolicLinkFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(symbolicLinkFile))) {
                return reader.readLine();
            }
        } else {
            throw new FileNotFoundException("Symbolic link file not found: " + linkName);
        }
    }
}
