package com.shashwat.hackcomms.workspace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollegeDomainTest {

    @Test
    void extractsCollegeFromSimpleDomain() {
        var c = CollegeDomain.fromEmail("ShashwatSingh.Chauhan@JIIT.com");
        assertThat(c.domain()).isEqualTo("jiit.com");
        assertThat(c.slug()).isEqualTo("jiit");
        assertThat(c.displayName()).isEqualTo("JIIT");
    }

    @Test
    void stripsMailSubdomainAndAcademicSuffix() {
        var c = CollegeDomain.fromEmail("22103001@mail.jiit.ac.in");
        assertThat(c.domain()).isEqualTo("jiit.ac.in");
        assertThat(c.slug()).isEqualTo("jiit");
    }

    @Test
    void handlesDepartmentSubdomains() {
        var c = CollegeDomain.fromEmail("a.b@cse.iitd.ac.in");
        assertThat(c.domain()).isEqualTo("cse.iitd.ac.in");
        assertThat(c.slug()).isEqualTo("iitd");
    }

    @Test
    void rejectsPersonalProviders() {
        assertThatThrownBy(() -> CollegeDomain.fromEmail("someone@gmail.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("college email");
    }

    @Test
    void rejectsMalformedEmail() {
        assertThatThrownBy(() -> CollegeDomain.fromEmail("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
