package com.shashwat.hackcomms.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Nexconn Chat Server API client (https://docs.nexconn.ai/platform-chat-api).
 * Every request is signed: Signature = hex(SHA1(appSecret + nonce + timestampMillis)).
 * The App Secret never leaves the backend.
 */
@Slf4j
@Component
public class NexconnClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String appKey;
    private final String appSecret;
    private final String areaCode;
    private final RestClient http;

    public NexconnClient(@Value("${hackcomms.nexconn.app-key:}") String appKey,
                         @Value("${hackcomms.nexconn.app-secret:}") String appSecret,
                         @Value("${hackcomms.nexconn.api-base}") String apiBase,
                         @Value("${hackcomms.nexconn.area-code}") String areaCode) {
        this.appKey = appKey.trim();
        this.appSecret = appSecret.trim();
        this.areaCode = areaCode.trim().toUpperCase();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        this.http = RestClient.builder().baseUrl(apiBase).requestFactory(factory).build();
        if (!isConfigured()) {
            log.warn("Nexconn App Key/Secret not set: chat features are disabled. See secrets.properties.example");
        }
    }

    public boolean isConfigured() {
        return !appKey.isEmpty() && !appSecret.isEmpty();
    }

    public String appKey() {
        return appKey;
    }

    public String areaCode() {
        return areaCode;
    }

    /** Registers the user (or refreshes their name) and returns a client access token. */
    public String issueToken(String userId, String name) {
        Map<String, Object> res = post("/v4/auth/access-token/issue", Map.of("userId", userId, "name", name));
        // v4 docs: {"code":0,"result":{"userId":..,"accessToken":..}}; older format: {"code":200,"token":..}
        Object result = res.get("result");
        if (result instanceof Map<?, ?> r && r.get("accessToken") instanceof String t) {
            return t;
        }
        if (res.get("token") instanceof String t) {
            return t;
        }
        throw new NexconnException(-1, "Token missing in response: " + res.keySet());
    }

    /** Creates a group channel. Already existing is treated as success. */
    public void createGroup(String channelId, String name, String ownerUserId, List<String> memberIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channelId", channelId);
        body.put("name", truncate(name, 64));
        body.put("owner", ownerUserId);
        if (!memberIds.isEmpty()) {
            body.put("userIds", memberIds.subList(0, Math.min(30, memberIds.size())));
        }
        try {
            post("/v4/group-channel/create", body);
        } catch (NexconnException e) {
            if (e.getCode() != NexconnException.GROUP_ALREADY_EXISTS) throw e;
        }
    }

    /** Adds users to a group channel. Already being a member is treated as success. */
    public void joinGroup(String channelId, List<String> userIds) {
        try {
            post("/v4/group-channel/join", Map.of("channelId", channelId, "userIds", userIds));
        } catch (NexconnException e) {
            if (e.getCode() != NexconnException.ALREADY_MEMBER) throw e;
        }
    }

    /** Removes users from a group channel. Not being a member is treated as success. */
    public void leaveGroup(String channelId, List<String> userIds) {
        try {
            post("/v4/group-channel/leave", Map.of("channelId", channelId, "userIds", userIds));
        } catch (NexconnException e) {
            if (e.getCode() != NexconnException.NOT_MEMBER) throw e;
        }
    }

    /** Freezing blocks every member from sending (read-only); history stays readable. */
    public void freezeGroup(String channelId) {
        post("/v4/group-channel/freeze-list/add", Map.of("channelIds", List.of(channelId)));
    }

    public void unfreezeGroup(String channelId) {
        post("/v4/group-channel/freeze-list/remove", Map.of("channelIds", List.of(channelId)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, ?> body) {
        if (!isConfigured()) {
            throw new NexconnException(-1, "Nexconn is not configured");
        }
        String nonce = Long.toString(Math.abs(RANDOM.nextLong()), 36);
        nonce = nonce.substring(0, Math.min(18, nonce.length()));
        String timestamp = Long.toString(System.currentTimeMillis());
        try {
            Map<String, Object> res = http.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("App-Key", appKey)
                    .header("Nonce", nonce)
                    .header("Timestamp", timestamp)
                    .header("Signature", sha1Hex(appSecret + nonce + timestamp))
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (res == null) throw new NexconnException(-1, "Empty response from " + path);
            int code = res.get("code") instanceof Number n ? n.intValue() : -1;
            if (code != 0 && code != 200) {
                throw new NexconnException(code, String.valueOf(res.getOrDefault("msg", res.get("errorMessage"))));
            }
            return res;
        } catch (RestClientResponseException e) {
            log.warn("Nexconn {} -> HTTP {}: {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new NexconnException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.warn("Nexconn {} failed: {}", path, e.getMessage());
            throw new NexconnException(-1, e.getMessage());
        }
    }

    private static String sha1Hex(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
