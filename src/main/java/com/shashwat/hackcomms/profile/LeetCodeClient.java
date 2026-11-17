package com.shashwat.hackcomms.profile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads public LeetCode profile data from leetcode.com/graphql (the same endpoint the site uses).
 * Unofficial, so every failure is handled as "couldn't reach LeetCode" rather than crashing.
 */
@Slf4j
@Component
public class LeetCodeClient {

    private static final String QUERY = """
            query($u: String!) {
              matchedUser(username: $u) {
                username
                profile { aboutMe }
                submitStatsGlobal { acSubmissionNum { difficulty count } }
              }
              userContestRanking(username: $u) {
                rating attendedContestsCount topPercentage badge { name }
              }
            }""";

    public record Stats(String username, String aboutMe, Integer rating, String badge, Double topPercent,
                        Integer contests, int solved, int easy, int medium, int hard) {}

    public static class LeetCodeUnavailableException extends RuntimeException {
        public LeetCodeUnavailableException(String msg) {
            super(msg);
        }
    }

    private final RestClient http;

    public LeetCodeClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(8_000);
        this.http = RestClient.builder()
                .baseUrl("https://leetcode.com")
                .requestFactory(factory)
                .defaultHeader("Referer", "https://leetcode.com")
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; HackComms/1.0)")
                .build();
    }

    /** @return empty if the user doesn't exist on LeetCode. */
    @SuppressWarnings("unchecked")
    public Optional<Stats> fetch(String username) {
        Map<String, Object> res;
        try {
            res = http.post().uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", QUERY, "variables", Map.of("u", username)))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.warn("LeetCode fetch for {} failed: {}", username, e.getMessage());
            throw new LeetCodeUnavailableException("Couldn't reach LeetCode right now. Try again in a minute.");
        }
        if (res == null || !(res.get("data") instanceof Map<?, ?> data)) {
            throw new LeetCodeUnavailableException("LeetCode returned an unexpected response.");
        }
        if (!(data.get("matchedUser") instanceof Map<?, ?> user)) {
            return Optional.empty();
        }

        String aboutMe = user.get("profile") instanceof Map<?, ?> p && p.get("aboutMe") instanceof String s ? s : "";
        int all = 0, easy = 0, medium = 0, hard = 0;
        if (user.get("submitStatsGlobal") instanceof Map<?, ?> st && st.get("acSubmissionNum") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m) || !(m.get("count") instanceof Number n)) continue;
                switch (String.valueOf(m.get("difficulty"))) {
                    case "All" -> all = n.intValue();
                    case "Easy" -> easy = n.intValue();
                    case "Medium" -> medium = n.intValue();
                    case "Hard" -> hard = n.intValue();
                    default -> { }
                }
            }
        }

        Integer rating = null, contests = null;
        Double top = null;
        String badge = null;
        if (data.get("userContestRanking") instanceof Map<?, ?> cr) {
            if (cr.get("attendedContestsCount") instanceof Number c) contests = c.intValue();
            // LeetCode reports 1500 for people who registered but never finished a contest; treat that as unrated.
            if (cr.get("rating") instanceof Number r && contests != null && contests > 0) rating = (int) Math.round(r.doubleValue());
            if (cr.get("topPercentage") instanceof Number t) top = t.doubleValue();
            if (cr.get("badge") instanceof Map<?, ?> b && b.get("name") instanceof String name) badge = name;
        }
        return Optional.of(new Stats(String.valueOf(user.get("username")), aboutMe, rating, badge, top, contests,
                all, easy, medium, hard));
    }
}
