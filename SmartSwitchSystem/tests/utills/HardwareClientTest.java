package utills;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import exception.HardwareException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class HardwareClientTest {

    private interface HardwareCall {
        void run() throws HardwareException;
    }

    private static int checks;

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        checks++;
    }

    private static void expectHardwareException(HardwareCall call, String label) throws Exception {
        try {
            call.run();
            throw new AssertionError(label);
        } catch (HardwareException expected) {
            checks++;
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try {
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            if (path.startsWith("/switch/on?")) {
                respond(exchange, 200, "{\"ok\":true}");
            } else if (path.startsWith("/switch/off?")) {
                respond(exchange, 503, "{\"error\":\"offline\"}");
            } else if (path.startsWith("/switch/status?gpio=2")) {
                respond(exchange, 200, "{\"gpio\":2,\"status\":\"on\"}");
            } else if (path.startsWith("/switch/status?")) {
                respond(exchange, 200, "{\"gpio\":7,\"status\":\"on\"}");
            } else if ("/device/info".equals(path)) {
                respond(exchange, 200, "{\"switches\":[{\"gpio\":2,\"status\":\"on\"},{\"gpio\":5,\"status\":\"off\"}]}");
            } else if ("/device/status".equals(path)) {
                respond(exchange, 200, "{\"status\":\"online\"}");
            } else {
                respond(exchange, 404, "{\"error\":\"missing\"}");
            }
        });
        server.start();
        try {
            String host = "http://127.0.0.1:" + server.getAddress().getPort();
            HardwareClient client = new HardwareClient(host);
            check(client.turnOn(2), "successful command returns true");
            expectHardwareException(() -> client.turnOff(2), "non-200 command must throw");
            check("ON".equals(client.getStatus(2)), "valid GPIO state is returned");
            expectHardwareException(() -> client.getStatus(5), "missing requested GPIO must throw");
            Map<Integer, String> statuses = client.getSwitchStatuses();
            check("ON".equals(statuses.get(2)) && "OFF".equals(statuses.get(5)),
                    "valid device info returns all switch states");
            check(client.isOnline(), "online device status is accepted");
            expectHardwareException(() -> client.turnOn(-1), "invalid GPIO must throw");
            expectHardwareException(() -> new HardwareClient("http://127.0.0.1:1").isOnline(),
                    "connection failure must throw");
        } finally {
            server.stop(0);
        }
        System.out.println("PASS: " + checks + " HardwareClient exception checks.");
    }
}
