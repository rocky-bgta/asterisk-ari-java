package org.simec.com;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.packet.namednumber.UdpPort;
import org.pcap4j.util.NifSelector;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

public class ARIWebSocketClientRTP extends WebSocketClient {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private String bridgeId;
    private static PcapHandle handle;
    private static AtomicBoolean capturing = new AtomicBoolean(false);
    private Thread captureThread;

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

                // Start capturing RTP packets
                startRtpPacketCapture();
            } else if ("StasisEnd".equals(eventType)) {
                String channelId = jsonData.get("channel").get("id").asText();
                System.out.println("Channel ID: " + channelId + " has left the Stasis application");

                // Stop capturing RTP packets
                stopRtpPacketCapture();

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

    private void startRtpPacketCapture() {
        capturing.set(true);
        captureThread = new Thread(() -> {
            try {
                PcapNetworkInterface nif = new NifSelector().selectNetworkInterface();
                if (nif == null) {
                    System.out.println("No network interface selected.");
                    return;
                }

                handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
                while (capturing.get()) {
                    Packet packet = handle.getNextPacketEx();
                    if (packet.contains(EtherType.IPV4) && packet.contains(UdpPort.RTP)) {
                        System.out.println("Captured RTP packet: " + packet);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        captureThread.start();
    }

    private void stopRtpPacketCapture() {
        capturing.set(false);
        if (handle != null) {
            handle.close();
        }
        try {
            if (captureThread != null) {
                captureThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
