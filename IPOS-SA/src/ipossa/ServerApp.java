package ipossa;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Entry point for the IPOS-SA prototype server.
 *
 * <p>This class boots the SQLite-backed data layer, starts the embedded HTTP
 * server, and serves both the REST API and the static frontend used for the
 * demo.</p>
 */
public final class ServerApp {
    private static final int DEFAULT_PORT = 8080;

    /**
     * Prevents instantiation of the application entry point.
     */
    private ServerApp() {
    }

    /**
     * Starts the IPOS-SA server.
     *
     * <p>Arguments are interpreted as:</p>
     *
     * <ol>
     *   <li>port</li>
     *   <li>database path</li>
     *   <li>static frontend root</li>
     * </ol>
     *
     * @param args optional startup arguments for port, database path, and
     *             static root
     * @throws Exception if the database or HTTP server cannot be started
     */
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
