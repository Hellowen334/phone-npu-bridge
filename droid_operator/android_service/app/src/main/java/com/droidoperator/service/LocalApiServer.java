package com.droidoperator.service;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class LocalApiServer {

    private static final String TAG = "DroidLocalApiServer";
    private final int port;
    private final DroidAccessibilityService service;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private Thread workerThread;

    public LocalApiServer(int port, DroidAccessibilityService service) {
        this.port = port;
        this.service = service;
    }

    public void start() {
        isRunning = true;
        workerThread = new Thread(() -> {
            try {
                // Bind exclusively to 127.0.0.1 (localhost) for zero external security risk
                serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
                Log.i(TAG, "Local server bound to 127.0.0.1:" + port);

                while (isRunning && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Server socket error", e);
                }
            }
        });
        workerThread.start();
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
    }

    private void handleClient(Socket socket) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream output = socket.getOutputStream()) {

                String requestLine = reader.readLine();
                if (requestLine == null) return;

                String[] parts = requestLine.split(" ");
                if (parts.length < 2) return;

                String method = parts[0];
                String path = parts[1];

                // Read headers
                int contentLength = 0;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }

                // Read body if POST
                StringBuilder body = new StringBuilder();
                if (contentLength > 0) {
                    char[] buffer = new char[contentLength];
                    int read = reader.read(buffer, 0, contentLength);
                    if (read > 0) {
                        body.append(buffer, 0, read);
                    }
                }

                String responseBody = "";
                int statusCode = 200;

                if ("GET".equalsIgnoreCase(method) && "/ui".equals(path)) {
                    responseBody = service.dumpUiJson();
                } else if ("POST".equalsIgnoreCase(method) && "/click".equals(path)) {
                    JSONObject json = new JSONObject(body.toString());
                    int x = json.getInt("x");
                    int y = json.getInt("y");
                    boolean success = service.click(x, y);
                    responseBody = "{\"success\":" + success + "}";
                } else if ("POST".equalsIgnoreCase(method) && "/type".equals(path)) {
                    JSONObject json = new JSONObject(body.toString());
                    String text = json.getString("text");
                    boolean success = service.typeText(text);
                    responseBody = "{\"success\":" + success + "}";
                } else if ("POST".equalsIgnoreCase(method) && "/swipe".equals(path)) {
                    JSONObject json = new JSONObject(body.toString());
                    int x1 = json.getInt("x1");
                    int y1 = json.getInt("y1");
                    int x2 = json.getInt("x2");
                    int y2 = json.getInt("y2");
                    int duration = json.optInt("duration", 300);
                    boolean success = service.swipe(x1, y1, x2, y2, duration);
                    responseBody = "{\"success\":" + success + "}";
                } else if ("POST".equalsIgnoreCase(method) && "/key".equals(path)) {
                    JSONObject json = new JSONObject(body.toString());
                    String action = json.getString("action");
                    boolean success = service.performGlobal(action);
                    responseBody = "{\"success\":" + success + "}";
                } else if ("POST".equalsIgnoreCase(method) && "/command".equals(path)) {
                    JSONObject json = new JSONObject(body.toString());
                    String cmd = json.getString("command");
                    service.executeAutonomousVoiceCommand(cmd);
                    responseBody = "{\"success\":true,\"executed\":\"" + cmd + "\"}";
                } else if ("GET".equalsIgnoreCase(method) && "/health".equals(path)) {
                    responseBody = "{\"status\":\"ok\",\"service\":\"DroidOperator\"}";
                } else {
                    statusCode = 404;
                    responseBody = "{\"error\":\"Not Found\"}";
                }

                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                String header = "HTTP/1.1 " + statusCode + " OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: " + responseBytes.length + "\r\n" +
                        "Connection: close\r\n\r\n";

                output.write(header.getBytes(StandardCharsets.UTF_8));
                output.write(responseBytes);
                output.flush();

            } catch (Exception e) {
                Log.e(TAG, "Request handling failed", e);
            }
        }).start();
    }
}
