package com.dealstracker.auth;

import com.dealstracker.domain.Interest;
import com.dealstracker.domain.ProductInterest;
import com.dealstracker.domain.User;
import com.dealstracker.repository.ProductInterestRepository;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PreferencesService {

    private final AuthService auth;
    private final ProductInterestRepository interests;

    public PreferencesService(AuthService auth, ProductInterestRepository interests) {
        this.auth = auth;
        this.interests = interests;
    }

    /** The caller's preferences; {@link UserPreferences#ANONYMOUS} when not logged in. */
    public UserPreferences forRequest(String authorizationHeader) {
        return auth.currentUser(authorizationHeader).map(this::forUser).orElse(UserPreferences.ANONYMOUS);
    }

    public UserPreferences forUser(User user) {
        Map<UUID, Interest> byProduct = interests.findByIdUserId(user.getId()).stream()
                .collect(Collectors.toMap(i -> i.getId().productId(), ProductInterest::getInterest));
        return new UserPreferences(byProduct, user.isShowSimilarProducts());
    }
}
