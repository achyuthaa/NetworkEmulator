import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

public class Bridge {
    private static void createSymbolicLink(String target, String link) {
        try {
            Path targetPath = Paths.get(target);
            Path linkPath = Paths.get(link);
            Files.createSymbolicLink(linkPath, targetPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("The usage is java Bridge lan-name num-ports");
            return;
        }

        int numPorts = Integer.parseInt(args[1]);
        String lanName = args[0];
        ServerSocketChannel sd_sock = null;

        try {
            sd_sock = ServerSocketChannel.open();
            sd_sock.socket().bind(new InetSocketAddress(InetAddress.getLocalHost(), 0), numPorts);
        } catch (Exception e) {
            System.out.println("Unable to create socket");
            e.printStackTrace();
            return;
        }

        InetAddress sd_address = ((InetSocketAddress) sd_sock.socket().getLocalSocketAddress()).getAddress();
        int port = sd_sock.socket().getLocalPort();
        String Ipaddressfilepath = "." + lanName + ".addr";
        String Portaddress = "." + lanName + ".port";
        createSymbolicLink(sd_address.getHostAddress(), Ipaddressfilepath);
        createSymbolicLink(String.valueOf(port), Portaddress);

        System.out.println("Server started on IP address " + sd_address.getHostAddress() + " and on port " + port);

        while (true) {
            // Your server logic here
        }
    }
}
