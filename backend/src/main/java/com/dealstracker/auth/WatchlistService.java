package com.dealstracker.auth;

import com.dealstracker.repository.WatchedProductRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WatchlistService {

    private final AuthService auth;
    private final WatchedProductRepository watched;

    public WatchlistService(AuthService auth, WatchedProductRepository watched) {
        this.auth = auth;
        this.watched = watched;
    }

    /** Products the caller marked as interesting; empty for anonymous callers. */
    public Set<UUID> watchedProductIds(String authorizationHeader) {
        return auth.currentUser(authorizationHeader)
                .map(u -> (Set<UUID>) new HashSet<>(watched.findProductIdsByUserId(u.getId())))
                .orElse(Set.of());
    }
}
