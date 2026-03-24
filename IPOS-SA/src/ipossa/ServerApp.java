package ipossa;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public final class ServerApp {
    private static final int DEFAULT_PORT = 8080;

    private ServerApp() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Path staticRoot = args.length > 2
                ? Paths.get(args[2]).toAbsolutePath()
                : Paths.get("").toAbsolutePath();
        Path dbPath = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath()
                : Paths.get("data", "ipos-sa.db").toAbsolutePath();

        Class.forName("org.sqlite.JDBC");

        Database database = new Database(dbPath);
        database.bootstrap();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ApiHandler(database, staticRoot));
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("IPOS-SA REST API started on port " + port);
        System.out.println("SQLite database: " + dbPath);
        System.out.println("Static frontend root: " + staticRoot);
        System.out.println("Default users: admin/admin123, manager/manager123, ops/ops123, accounts/accounts123, merchant1/merchant123");
    }
}
