package org.simec.com;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.concurrent.TimeUnit;

public class ARIUtility {
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String createBridge(String serverUrl, String user, String password) throws Exception {
        String url = serverUrl + "/ari/bridges";
        String data = "{\"type\":\"mixing\"}";
        return postRequest(url, data, user, password);
    }

    public static String createChannel(String serverUrl, String endpoint, String extension, String context, int priority, String app, String user, String password) throws Exception {
        String url = serverUrl + "/ari/channels/create";
        String data = String.format("{\"endpoint\":\"%s\", \"extension\":\"%s\", \"context\":\"%s\", \"priority\":%d, \"app\":\"%s\"}",
                endpoint, extension, context, priority, app);
        return postRequest(url, data, user, password);
    }

    public static void addChannelToBridge(String serverUrl, String bridgeId, String channelId, String user, String password) throws Exception {
        String url = serverUrl + "/ari/bridges/" + bridgeId + "/addChannel";
        String data = "channel=" + channelId;
        postUrlEncodedRequest(url, data, user, password);
    }

    public static void dialChannel(String serverUrl, String channelId, String user, String password) throws Exception {
        String url = serverUrl + "/ari/channels/" + channelId + "/dial";
        postRequest(url, "", user, password);
    }

    public static void deleteBridge(String serverUrl, String bridgeId, String user, String password) throws Exception {
        String url = serverUrl + "/ari/bridges/" + bridgeId;
        deleteRequest(url, user, password);
    }

    private static String postRequest(String url, String data, String user, String password) throws Exception {
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
            if (!responseBody.isEmpty()) {
                JsonNode responseObject = objectMapper.readTree(responseBody);
                return responseObject.get("id").asText();
            }else {
                return null;
            }

        } catch (Exception e) {
            System.err.println("Failed to perform HTTP request: " + e.getMessage());
            throw e;
        }
    }

    private static void postUrlEncodedRequest(String url, String data, String user, String password) throws Exception {
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

    private static void deleteRequest(String url, String user, String password) throws Exception {
        String credential = Credentials.basic(user, password);

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", credential)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            if (responseBody.isEmpty()) {
                System.out.println("Empty response body from server");
            } else {
                System.out.println("Response: " + responseBody);
            }
        } catch (Exception e) {
            System.err.println("Failed to perform HTTP request: " + e.getMessage());
            throw e;
        }
    }
}
