package com.dealstracker.auth;

import com.dealstracker.domain.Interest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a user told us about products: which they watch, which they don't care about, and whether
 * "cheapest" insights may suggest similar products (same size / stage, other brands).
 */
public record UserPreferences(Map<UUID, Interest> interests, boolean showSimilarProducts) {

    public static final UserPreferences ANONYMOUS = new UserPreferences(Map.of(), true);

    public Set<UUID> watched() {
        return with(Interest.WATCHED);
    }

    public Set<UUID> notInterested() {
        return with(Interest.NOT_INTERESTED);
    }

    public Interest interestIn(UUID productId) {
        return interests.get(productId);
    }

    private Set<UUID> with(Interest interest) {
        return interests.entrySet().stream()
                .filter(e -> e.getValue() == interest)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
