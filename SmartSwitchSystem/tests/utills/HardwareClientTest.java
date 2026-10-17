package utills;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import exception.HardwareException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
        AtomicReference<String> scheduleAddRequest = new AtomicReference<>();
        AtomicReference<String> scheduleDeleteRequest = new AtomicReference<>();
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
            } else if (path.startsWith("/schedule/add?")) {
                scheduleAddRequest.set(path);
                respond(exchange, 200, "{\"result\":\"ok\"}");
            } else if (path.startsWith("/schedule/delete?")) {
                scheduleDeleteRequest.set(path);
                if (path.endsWith("id=99")) {
                    respond(exchange, 503, "{\"error\":\"offline\"}");
                    return;
                }
                respond(exchange, 200, "{\"result\":\"ok\"}");
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
            check(client.addOrUpdateSchedule(12, 18, java.time.LocalTime.of(7, 30), "ON", true),
                    "schedule add/update API success is accepted");
            check("/schedule/add?id=12&gpio=18&time=07%3A30&action=ON&enabled=true"
                    .equals(scheduleAddRequest.get()), "schedule parameters match ESP32 API format");
            check(client.addOrUpdateSchedule(13, 5, java.time.LocalTime.of(19, 0), "OFF", false),
                    "disabled OFF schedules are accepted");
            check("/schedule/add?id=13&gpio=5&time=19%3A00&action=OFF&enabled=false"
                    .equals(scheduleAddRequest.get()), "disabled flag and OFF action are transmitted");
            check(client.deleteSchedule(12), "schedule delete API success is accepted");
            check("/schedule/delete?id=12".equals(scheduleDeleteRequest.get()),
                    "schedule delete ID matches ESP32 API format");
            expectHardwareException(() -> client.deleteSchedule(99),
                    "non-200 schedule response must throw");
            expectHardwareException(() -> client.addOrUpdateSchedule(0, 18,
                    java.time.LocalTime.of(7, 30), "ON", true),
                    "non-positive schedule IDs must be rejected");
            expectHardwareException(() -> client.addOrUpdateSchedule(12, 18,
                    java.time.LocalTime.of(7, 30), "TOGGLE", true),
                    "unsupported schedule actions must be rejected");
            expectHardwareException(() -> client.turnOn(-1), "invalid GPIO must throw");
            expectHardwareException(() -> new HardwareClient("http://127.0.0.1:1").isOnline(),
                    "connection failure must throw");
        } finally {
            server.stop(0);
        }
        System.out.println("PASS: " + checks + " HardwareClient exception checks.");
    }
}
