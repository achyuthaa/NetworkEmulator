import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class client {
    static Map<String,String> Hosts = new HashMap<>();
    public static void main(String[] args) {
        //Host to IpMapping
        String hostpath = "Hosts/hosts";
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

        // Display the stored information in a table-like format
        System.out.println("+-------------------+-------------------+");
        System.out.println("|     Host Name    |     IP Address    |");
        System.out.println("+-------------------+-------------------+");

        for (Map.Entry<String, String> entry : Hosts.entrySet()) {
            System.out.printf("| %-17s | %-17s |\n", entry.getKey(), entry.getValue());
        }

        System.out.println("+-------------------+-------------------+");

        Scanner sc = new Scanner(System.in);

        String serverIP = null;
        try {
            serverIP = readSymbolicLink(".cs1.addr");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int serverPort = 0;
        try {
            serverPort = Integer.parseInt(readSymbolicLink(".cs1.port"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            Selector selector = Selector.open();

            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);

            SocketAddress serverAddress = new InetSocketAddress(InetAddress.getByName(serverIP), serverPort);
            socketChannel.connect(serverAddress);
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            // Create a thread to handle user input
            Thread userInputThread = new Thread(() -> {
                while (true) {
                    if (sc.hasNextLine()) {
                        String userInput = sc.nextLine();
                        try {
                            ByteBuffer buffer = ByteBuffer.wrap(userInput.getBytes());
                            socketChannel.write(buffer);
                        } catch (IOException e) {
                            e.printStackTrace();
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
                    }

                    else if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        int bytesRead = channel.read(buffer);
                        if (bytesRead == -1) {
                            key.cancel();
                            channel.close();
                            return;
                        }
                        buffer.flip();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        String response = new String(data);
                        System.out.println("Received: " + response);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
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
