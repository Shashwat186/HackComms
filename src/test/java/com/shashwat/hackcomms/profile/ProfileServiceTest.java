package com.shashwat.hackcomms.profile;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileServiceTest {

    @Test
    void parsesUsernamesAndProfileUrls() {
        assertThat(ProfileService.parseUsername("lee215")).isEqualTo("lee215");
        assertThat(ProfileService.parseUsername(" @lee215 ")).isEqualTo("lee215");
        assertThat(ProfileService.parseUsername("https://leetcode.com/u/lee215/")).isEqualTo("lee215");
        assertThat(ProfileService.parseUsername("leetcode.com/lee215")).isEqualTo("lee215");
    }

    @Test
    void rejectsGarbageUsernames() {
        assertThatThrownBy(() -> ProfileService.parseUsername("not a user!")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ProfileService.parseUsername("")).isInstanceOf(ResponseStatusException.class);
    }
}
