package com.shashwat.hackcomms.workspace;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns an email address into the college it belongs to.
 *
 * <pre>
 *   shashwatsingh.chauhan@jiit.com      -> domain "jiit.com",     slug "jiit"
 *   2210xxxx@mail.jiit.ac.in            -> domain "jiit.ac.in",   slug "jiit"
 *   someone@gmail.com                   -> rejected (public provider)
 * </pre>
 *
 * The workspace is keyed by the full domain, not the slug: owning "jiit.com" doesn't
 * prove you're the same institution as "jiit.ac.in", so they stay separate workspaces.
 */
public record CollegeDomain(String domain, String slug, String displayName) {

    private static final Pattern EMAIL =
            Pattern.compile("^[a-z0-9._%+-]+@([a-z0-9-]+\\.)+[a-z]{2,}$");

    /** Mail-server subdomains colleges put in front of their real domain. */
    private static final List<String> STRIP_PREFIXES =
            List.of("mail.", "webmail.", "student.", "students.", "stu.", "std.", "alumni.");

    /** Second-level labels that are part of the public suffix, e.g. "ac" in "jiit.ac.in". */
    private static final Set<String> SECOND_LEVEL = Set.of("ac", "edu", "co", "org", "gov", "res", "net");

    private static final Set<String> PUBLIC_PROVIDERS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.in", "yahoo.co.in", "ymail.com",
            "outlook.com", "hotmail.com", "live.com", "msn.com", "icloud.com", "me.com",
            "proton.me", "protonmail.com", "aol.com", "zoho.com", "zohomail.in", "rediffmail.com",
            "gmx.com", "yandex.com", "mail.com", "tutanota.com");

    public static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** @throws IllegalArgumentException with a user-facing message if the email can't join a workspace. */
    public static CollegeDomain fromEmail(String rawEmail) {
        String email = normaliseEmail(rawEmail);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        String domain = email.substring(email.indexOf('@') + 1);
        if (PUBLIC_PROVIDERS.contains(domain)) {
            throw new IllegalArgumentException("Use your college email, not a personal " + domain + " address.");
        }
        for (String prefix : STRIP_PREFIXES) {
            if (domain.startsWith(prefix) && domain.indexOf('.', prefix.length()) > 0) {
                domain = domain.substring(prefix.length());
                break;
            }
        }
        String slug = slugFor(domain);
        return new CollegeDomain(domain, slug, displayNameFor(slug));
    }

    /** "jiit.ac.in" -> "jiit", "jiit.com" -> "jiit", "cs.stanford.edu" -> "stanford". */
    private static String slugFor(String domain) {
        String[] labels = domain.split("\\.");
        int i = labels.length - 2; // label just left of the TLD
        if (i > 0 && SECOND_LEVEL.contains(labels[i])) {
            i--;
        }
        return labels[Math.max(i, 0)].replaceAll("[^a-z0-9-]", "");
    }

    /** Short slugs are usually acronyms (JIIT, IITD, BITS); longer ones get title case. */
    private static String displayNameFor(String slug) {
        if (slug.length() <= 5) {
            return slug.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
    }
}
