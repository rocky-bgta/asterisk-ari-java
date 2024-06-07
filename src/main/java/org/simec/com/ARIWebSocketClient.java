package org.simec.com;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class ARIWebSocketClient extends WebSocketClient {
    private static final String SERVER_URL = "http://192.168.0.179:8088";
    private static final String USER = "asterisk";
    private static final String PASSWORD = "secret";
    private static final String CONTEXT = "default";
    private static final String STASIS_APP = "my-stasis-app";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // Increased timeout
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public ARIWebSocketClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("Connection opened");
    }

    @Override
    public void onMessage(String message) {
        System.out.println("Received message: " + message);

        try {
            JsonNode jsonData = objectMapper.readTree(message);
            String eventType = jsonData.get("type").asText();
            System.out.println("Event Type: " + eventType);

            if ("StasisStart".equals(eventType)) {
                String channelId = jsonData.get("channel").get("id").asText();
                String callerNumber = jsonData.get("channel").get("caller").get("number").asText();
                System.out.println("Incoming call from: " + callerNumber + " on channel ID: " + channelId);

                String targetExtension = jsonData.get("channel").get("dialplan").get("exten").asText();
                System.out.println("Target extension: " + targetExtension);

                String bridgeId = createBridge();
                System.out.println("Bridge created with ID: " + bridgeId);

                String SIPInfo = "SIP/" + targetExtension;

                String newChannelId = createChannel(SERVER_URL, SIPInfo, targetExtension, CONTEXT, 1, STASIS_APP, USER, PASSWORD);
                System.out.println("New channel created with ID: " + newChannelId);

                addChannelToBridge(SERVER_URL, bridgeId, channelId, USER, PASSWORD);
                System.out.println("Active channel " + channelId + " added to bridge " + bridgeId);

                addChannelToBridge(SERVER_URL, bridgeId, newChannelId, USER, PASSWORD);
                System.out.println("New channel " + newChannelId + " added to bridge " + bridgeId);

                dialChannel(SERVER_URL, newChannelId, USER, PASSWORD);
                System.out.println("Dialing new channel " + newChannelId);
            } else if ("StasisEnd".equals(eventType)) {
                String channelId = jsonData.get("channel").get("id").asText();
                System.out.println("Channel ID: " + channelId + " has left the Stasis application");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Connection closed: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    private String createBridge() throws Exception {
        String url = SERVER_URL + "/ari/bridges";
        String data = "{\"type\":\"mixing\"}";
        return postRequest(url, data, USER, PASSWORD);
    }

    private String createChannel(String serverUrl, String endpoint, String extension, String context, int priority, String app, String user, String password) throws Exception {
        String url = serverUrl + "/ari/channels/create";
        String data = String.format("{\"endpoint\":\"%s\", \"extension\":\"%s\", \"context\":\"%s\", \"priority\":%d, \"app\":\"%s\"}",
                endpoint, extension, context, priority, app);
        return postRequest(url, data, user, password);
    }

    private void addChannelToBridge(String serverUrl, String bridgeId, String channelId, String user, String password) throws Exception {
        String url = serverUrl + "/ari/bridges/" + bridgeId + "/addChannel";
        String data = "channel=" + channelId;
        postUrlEncodedRequest(url, data, user, password);
    }

    private void dialChannel(String serverUrl, String channelId, String user, String password) throws Exception {
        String url = serverUrl + "/ari/channels/" + channelId + "/dial";
        postRequest(url, "", user, password);
    }

    private String postRequest(String url, String data, String user, String password) throws Exception {
        RequestBody body = RequestBody.create(data, MediaType.parse("application/json"));
        String credential = Credentials.basic(user, password);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", credential)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            JsonNode responseObject = objectMapper.readTree(responseBody);
            return responseObject.get("id").asText();
        } catch (Exception e) {
            System.err.println("Failed to perform HTTP request: " + e.getMessage());
            throw e;
        }
    }

    private void postUrlEncodedRequest(String url, String data, String user, String password) throws Exception {
        RequestBody body = RequestBody.create(data, MediaType.parse("application/x-www-form-urlencoded"));
        String credential = Credentials.basic(user, password);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", credential)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Unexpected code " + response);
            }
            System.out.println("Response: " + response.body().string());
        } catch (Exception e) {
            System.err.println("Failed to perform HTTP request: " + e.getMessage());
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            String ipAddress = "192.168.0.179:8088";
            String uri = "ws://" + ipAddress + "/ari/events?api_key=asterisk:secret&app=my-stasis-app";
            ARIWebSocketClient client = new ARIWebSocketClient(new URI(uri));
            client.connectBlocking();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
