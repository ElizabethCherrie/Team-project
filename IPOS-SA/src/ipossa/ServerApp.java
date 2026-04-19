package ipossa;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

        startServer(port, dbPath, staticRoot);
    }

    /**
     * Starts the embedded IPOS-SA HTTP server using the supplied runtime configuration.
     *
     * @param port the port to bind the server to
     * @param dbPath the SQLite database file to use
     * @param staticRoot the folder containing the static frontend assets
     * @return the started HTTP server instance
     * @throws Exception if the database or HTTP server cannot be initialized
     */
    static HttpServer startServer(int port, Path dbPath, Path staticRoot) throws Exception {
        Class.forName("org.sqlite.JDBC");

        IntegrationClient integrationClient = IntegrationClient.fromEnvironment();
        Database database = new Database(dbPath, integrationClient);
        database.bootstrap();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ApiHandler(database, staticRoot));
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        ScheduledExecutorService sweepScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "account-status-sweep");
            t.setDaemon(true);
            return t;
        });
        sweepScheduler.scheduleAtFixedRate(database::runAccountStatusSweep, 0, 1, TimeUnit.HOURS);

        System.out.println("IPOS-SA REST API started on port " + port);
        System.out.println("SQLite database: " + dbPath);
        System.out.println("Static frontend root: " + staticRoot);
        System.out.println("Seeded users: Sysdba/London_weighting, manager/Get_it_done, accountant/Count_money, delivery/Too_dark, city/demo123");
        System.out.println("Integration config: " + integrationClient.describeConfiguration());
        return server;
    }
}
