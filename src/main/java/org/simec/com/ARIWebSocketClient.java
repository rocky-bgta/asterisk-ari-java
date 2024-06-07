package org.simec.com;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class ARIWebSocketClient extends WebSocketClient {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private String bridgeId;

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

            if ("StasisStart".equals(eventType) && !"ChannelDialplan".equals(eventType)) {
                String channelId = jsonData.get("channel").get("id").asText();
                String callerNumber = jsonData.get("channel").get("caller").get("number").asText();
                System.out.println("Incoming call from: " + callerNumber + " on channel ID: " + channelId);

                String targetExtension = jsonData.get("channel").get("dialplan").get("exten").asText();
                System.out.println("Target extension: " + targetExtension);

                bridgeId = ARIUtility.createBridge(Constants.SERVER_URL, Constants.USER, Constants.PASSWORD);
                System.out.println("Bridge created with ID: " + bridgeId);

                String SIPInfo = "SIP/" + targetExtension;

                String newChannelId = ARIUtility.createChannel(Constants.SERVER_URL, SIPInfo, targetExtension, Constants.CONTEXT, 1, Constants.STASIS_APP, Constants.USER, Constants.PASSWORD);
                System.out.println("New channel created with ID: " + newChannelId);

                ARIUtility.addChannelToBridge(Constants.SERVER_URL, bridgeId, channelId, Constants.USER, Constants.PASSWORD);
                System.out.println("Active channel " + channelId + " added to bridge " + bridgeId);

                ARIUtility.addChannelToBridge(Constants.SERVER_URL, bridgeId, newChannelId, Constants.USER, Constants.PASSWORD);
                System.out.println("New channel " + newChannelId + " added to bridge " + bridgeId);

                ARIUtility.dialChannel(Constants.SERVER_URL, newChannelId, Constants.USER, Constants.PASSWORD);
                System.out.println("Dialing new channel " + newChannelId);
            } else if ("StasisEnd".equals(eventType)) {
                String channelId = jsonData.get("channel").get("id").asText();
                System.out.println("Channel ID: " + channelId + " has left the Stasis application");


                // Clear the bridge
                if (bridgeId != null) {
                    ARIUtility.deleteBridge(Constants.SERVER_URL, bridgeId, Constants.USER, Constants.PASSWORD);
                    System.out.println("Bridge " + bridgeId + " deleted");
                    bridgeId = null;
                }
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

    public static void main(String[] args) {
        try {
            String ipAddress = Constants.SERVER_URL.replace("http://", "").replace(":8088", "");
            String uri = "ws://" + ipAddress + ":8088/ari/events?api_key=" + Constants.USER + ":" + Constants.PASSWORD + "&app=" + Constants.STASIS_APP;
            ARIWebSocketClient client = new ARIWebSocketClient(new URI(uri));
            client.connectBlocking();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
