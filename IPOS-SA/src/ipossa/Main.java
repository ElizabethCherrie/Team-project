package ipossa;

import java.awt.Desktop;
import java.net.BindException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Demo-friendly launcher for the IPOS-SA subsystem.
 *
 * <p>This entry point starts the embedded server and then attempts to open the
 * login page in the default browser so the subsystem can be demonstrated from
 * a single main class in IntelliJ or from the command line.</p>
 */
public final class Main {
    private static final int DEFAULT_PORT = 8080;

    private Main() {
    }

    /**
     * Starts the subsystem and opens the browser to the login page.
     *
     * <p>Arguments are interpreted in the same way as {@link ServerApp}:
     * port, database path, and static frontend root.</p>
     *
     * @param args optional startup arguments for port, database path, and static root
     * @throws Exception if the server cannot be started
     */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Path appRoot = resolveAppRoot();
        Path dbPath = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath()
                : appRoot.resolve(Paths.get("data", "ipos-sa.db")).toAbsolutePath();
        Path staticRoot = args.length > 2
                ? Paths.get(args[2]).toAbsolutePath()
                : appRoot.toAbsolutePath();

        try {
            ServerApp.startServer(port, dbPath, staticRoot);
            openBrowser("http://localhost:" + port + "/login.html");
        } catch (BindException ex) {
            String url = "http://localhost:" + port + "/login.html";
            System.out.println("Port " + port + " is already in use. Assuming IPOS-SA is already running.");
            openBrowser(url);
        }
    }

    /**
     * Resolves the application root folder when launched from either the module directory
     * or the parent coursework directory in IntelliJ.
     *
     * @return the folder containing the IPOS-SA static assets and data directory
     */
    private static Path resolveAppRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (cwd.resolve("data").toFile().exists() && cwd.resolve("login.html").toFile().exists()) {
            return cwd;
        }
        Path nested = cwd.resolve("IPOS-SA");
        if (nested.resolve("data").toFile().exists() && nested.resolve("login.html").toFile().exists()) {
            return nested;
        }
        return cwd;
    }

    /**
     * Attempts to open the supplied URL in the default desktop browser.
     *
     * @param url the URL to open
     */
    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                System.out.println("Open this URL in a browser: " + url);
            }
        } catch (Exception ex) {
            System.out.println("Open this URL in a browser: " + url);
        }
    }
}
