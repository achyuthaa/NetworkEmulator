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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class client {
    static Map<String, String[]> iptable = new HashMap<>();
    static Map<String, String> Hosts = new HashMap<>();
    static Map<String, String[]> rtable = new HashMap<>();
    static Map<String, String> Arpcache = new HashMap<>();
    static Map<String, SocketChannel> path = new HashMap<>();

    static Map<String, Integer> serverss = new HashMap<>();
    static String ResultIP = "";
    static String srcinterface = "";
    static String sourceip = "";
    static String Sourcemac = "";

    static Queue<PacketQ> packetqueue = new LinkedList<>();

    public static void Sendobject(Ethernetframe frame,SocketChannel sd){
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
            e.printStackTrace();
        }
    }

    public static void storemacandip(){

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
        Selector selector = Selector.open();

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

                    }  else if (userInput.equals("show arpcache")) {
                    System.out.println("+-------------------+-------------------+");
                    System.out.println("|   IP Address     |     MAC Address   |");
                    System.out.println("+-------------------+-------------------+");

                    for (Map.Entry<String, String> entry : Arpcache.entrySet()) {
                        String ipAddress = entry.getKey();
                        String macAddress = entry.getValue();

                        System.out.printf("| %-17s | %-17s |\n", ipAddress, macAddress);
                    }

                    System.out.println("+-------------------+-------------------+");
                }

                else if (parts.length >= 3 && "send".equals(parts[0])) {
                        //String[] parts = userInput.split(" ");
                        String recipient = parts[1];
                        String message = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
                        String Destinationip = Hosts.get(recipient);
                        System.out.println("Destinatip check"+" "+Destinationip);
                        sourceip = "";
                        String nexthop = null;
                        String tnexthop = null;
                        Sourcemac = null;
                        String Destinationmac = null;
                        for (Map.Entry<String, String[]> entry : rtable.entrySet()) {
                            tnexthop = entry.getKey();
                            String values[] = entry.getValue();
                            String subnetmask = values[1];
                            System.out.println(subnetmask);

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
                        System.out.println("nexthopcheck"+ " "+nexthop);


                        //Dataframe data = new Dataframe.Builder(message).SourceIpaddress(sourceip).DestinationIpaddress(Destinationip).Arptype(0).build();
                        Dataframe data = new Dataframe.Builder().Data(message).build();
                        Ipframe pack = new Ipframe.Builder().SourceIpaddress(sourceip).destinationIP(Destinationip).build();
                        String[] arr = iptable.get(srcinterface);
                        Sourcemac = arr[2];
                        String bri = arr[3];

                        if(nexthop.equals("0.0.0.0")){
                            Ipframe arppack = null;
                            if(!Arpcache.containsKey(pack.getDestinationIp())){
                                PacketQ Qpack = new PacketQ.Builder().iframe(pack).nextHop(Destinationip).build();
                                packetqueue.add(Qpack);
                                arppack = new ARPframe.Builder("1").Sourcemac(Sourcemac).SourceIpaddress(sourceip).destinationIP(Destinationip).build();
                            }
                            else{
                                for (PacketQ packetout: packetqueue) {
                                    if(packetout.getNextHop().equals(pack.getDestinationIp())){
                                        Ipframe ippackretreived = packetout.getIframe();
                                        Ethernetframe Arpreq =  new Ethernetframe.Builder().getType(1).SourceMacAddress(Sourcemac).ipframe(ippackretreived).build();
                                    }
                                }
                            }
                            Ethernetframe Arpreq =  new Ethernetframe.Builder().getType(1).SourceMacAddress(Sourcemac).ipframe(arppack).build();
                            try {
                                ByteBuffer buffer = ByteBuffer.allocate(1024);
                                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteStream);
                                objectOutputStream.writeObject(Arpreq);
                                objectOutputStream.flush();

                                byte[] bytes = byteStream.toByteArray();
                                buffer.put(bytes);
                                buffer.flip();
                                SocketChannel sd = path.get(bri);
                                for (Map.Entry<String, SocketChannel> entry : path.entrySet()) {
                                    String a = entry.getKey();
                                    SocketChannel b = entry.getValue();
                                    System.out.println(a + " " + b);
                                }

                                sd.write(buffer);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                       else{
                           if (!Arpcache.containsKey(nexthop)) {
                                PacketQ Qpack = new PacketQ.Builder().iframe(pack).nextHop(nexthop).build();
                                packetqueue.add(Qpack);
                                //ARPframe pack = new ARPframe.Builder().Arptype(1).DestinationIp(nexthop).Sourcemac(Sourcemac).SourceIp(sourceip).build();
                                //Ipframe arpack = new Ipframe.Builder().SourceIpaddress(sourceip).destinationIP(nexthop).build();
                                Ipframe arppack = null;
                                arppack = new ARPframe.Builder("1").Sourcemac(Sourcemac).SourceIpaddress(sourceip).destinationIP(nexthop).build();

                                Ethernetframe Arpreq =  new Ethernetframe.Builder().getType(1).SourceMacAddress(Sourcemac).ipframe(arppack).build();
                                try {
                                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteStream);
                                    objectOutputStream.writeObject(Arpreq);
                                    objectOutputStream.flush();

                                    byte[] bytes = byteStream.toByteArray();
                                    buffer.put(bytes);
                                    buffer.flip();
                                    SocketChannel sd = path.get(bri);
                                    for (Map.Entry<String, SocketChannel> entry : path.entrySet()) {
                                        String a = entry.getKey();
                                        SocketChannel b = entry.getValue();
                                        System.out.println(a + " " + b);
                                    }

                                    sd.write(buffer);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            } else if (Arpcache.containsKey(nexthop)) {
                                String destmac = Arpcache.get(nexthop);
                                PacketQ packtosend = null;
                                for (PacketQ Qpack : packetqueue
                                ) {
                                    if (Qpack.getNextHop().equals(nexthop)) {
                                        packtosend = packetqueue.poll();
                                        break;
                                    }
                                }
                                Ipframe packet = packtosend.getIframe();
                                Ethernetframe packready = new Ethernetframe.Builder().getType(0).DestinationMacAddress(destmac).SourceMacAddress(Sourcemac).ipframe(packet).build();
                                try {
                                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteStream);
                                    objectOutputStream.writeObject(packready);
                                    objectOutputStream.flush();

                                    byte[] bytes = byteStream.toByteArray();
                                    buffer.put(bytes);
                                    buffer.flip();
                                    SocketChannel sd = path.get(bri);
                                    for (Map.Entry<String, SocketChannel> entry : path.entrySet()) {
                                        String a = entry.getKey();
                                        SocketChannel b = entry.getValue();
                                        System.out.println(a + " " + b);
                                    }

                                    sd.write(buffer);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                        }
                    }
                }
            }

        });


        // Start the user input thread
        userInputThread.start();

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
                        System.out.println(receivedObject);

                        if (receivedObject instanceof Ethernetframe) {
                            Ethernetframe receivedData = (Ethernetframe) receivedObject;
                            Ipframe receivedObjec = receivedData.getIframe();
                            String Arptype = "";
                            if(receivedObjec instanceof ARPframe) {
                                ARPframe receivedarp = (ARPframe) receivedObjec;
                                if (receivedarp.getArptype() == null) {
                                    Arptype = "0";
                                } else {
                                    Arptype = receivedarp.getArptype();
                                }

                                String arpsrc = receivedData.getSourceMacAddress();

                                String arpsip = receivedarp.getSourceIP();
                                String arpdip = receivedarp.getDestinationIp();

                                //Arpcache.put(arpsip,arpsrc);
                                Ipframe backarp = null;
                                if (Arptype.equals("1")) {
                                    System.out.println("Received Arprequest");
                                    String sourceMacAddress = receivedData.getSourceMacAddress();
                                    String destinationMacAddress = receivedData.getDestinationMacAddress();
                                    System.out.println("Received Ethernetframe: src MAC " + sourceMacAddress + ", Dest MAC " + destinationMacAddress + " ArpType: " + receivedData.getType());
                                    for (Map.Entry<String, String[]> entry : iptable.entrySet()) {
                                        String values[] = entry.getValue();
                                        if (values[0].equals(arpdip)) {
                                            Sourcemac = values[2];
                                            sourceip = values[0];
                                        }


                                    }
                                    Arpcache.put(arpsip,arpsrc);
                                    System.out.println("reachedthis " + Sourcemac + " " + sourceip + " " + receivedarp.getDestinationIp());
                                    if (receivedarp.getDestinationIp().equals(sourceip)) {
                                        System.out.println("Sent Arpresponse");
                                        backarp = new ARPframe.Builder("2").Sourcemac(arpsrc).Destinationmac(Sourcemac).destinationIP(arpsip).SourceIpaddress(arpdip).build();
                                    }

                                }
                                String sourceMacAddress = Sourcemac;
                                String destinationMacAddress = receivedData.getSourceMacAddress();
                                Ethernetframe frame = new Ethernetframe.Builder().SourceMacAddress(sourceMacAddress).DestinationMacAddress(destinationMacAddress).ipframe(backarp).build();
                                Sendobject(frame, channel);

                                if (Arptype.equals("2")) {
                                    System.out.println("Received Arpresponse");
                                    String destinmac = receivedarp.getDestinationmac();
                                    String destip = receivedarp.getSourceIP();
                                    Arpcache.put(destip,destinmac);
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
                } else if (key.isReadable() && args[0].equals("-route")) {

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
