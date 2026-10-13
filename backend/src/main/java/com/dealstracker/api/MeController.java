package com.dealstracker.api;

import com.dealstracker.api.Dtos.InterestRequest;
import com.dealstracker.api.Dtos.SettingsRequest;
import com.dealstracker.api.Dtos.UserDto;
import com.dealstracker.auth.AuthService;
import com.dealstracker.domain.ProductInterest;
import com.dealstracker.domain.User;
import com.dealstracker.repository.ProductInterestRepository;
import com.dealstracker.repository.ProductRepository;
import com.dealstracker.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The logged-in user's settings and product marks (watched / not interested). */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthService auth;
    private final UserRepository users;
    private final ProductInterestRepository interests;
    private final ProductRepository products;

    public MeController(AuthService auth, UserRepository users, ProductInterestRepository interests,
                        ProductRepository products) {
        this.auth = auth;
        this.users = users;
        this.interests = interests;
        this.products = products;
    }

    @PutMapping("/settings")
    public UserDto updateSettings(@Valid @RequestBody SettingsRequest req,
                                  @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                  String authorization) {
        User user = auth.requireUser(authorization);
        user.setShowSimilarProducts(req.showSimilarProducts());
        return AuthController.user(users.save(user));
    }

    @PutMapping("/products/{productId}/interest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setInterest(@PathVariable UUID productId, @Valid @RequestBody InterestRequest req,
                            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                            String authorization) {
        User user = auth.requireUser(authorization);
        if (!products.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product");
        }
        ProductInterest.Key key = new ProductInterest.Key(user.getId(), productId);
        ProductInterest mark = interests.findById(key)
                .orElseGet(() -> new ProductInterest(user.getId(), productId, req.interest()));
        mark.setInterest(req.interest());
        interests.save(mark);
    }

    @DeleteMapping("/products/{productId}/interest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearInterest(@PathVariable UUID productId,
                              @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                              String authorization) {
        User user = auth.requireUser(authorization);
        interests.deleteById(new ProductInterest.Key(user.getId(), productId));
    }
}
