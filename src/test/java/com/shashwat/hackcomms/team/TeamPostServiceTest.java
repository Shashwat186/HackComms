package com.shashwat.hackcomms.team;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamPostServiceTest {

    @Test
    void normaliseSkillsTrimsDedupesAndCaps() {
        List<String> out = TeamPostService.normaliseSkills(Arrays.asList(
                "  React ", "react", "", null, "Machine   Learning", "UI,UX", "a", "b", "c", "d", "e", "f", "g"));
        assertThat(out).containsExactly("React", "Machine Learning", "UI UX", "a", "b", "c", "d", "e");
    }

    @Test
    void normaliseSkillsHandlesNullAndLongTags() {
        assertThat(TeamPostService.normaliseSkills(null)).isEmpty();
        assertThat(TeamPostService.normaliseSkills(List.of("x".repeat(40)))).containsExactly("x".repeat(24));
    }
}
