package com.dealstracker.api;

import com.dealstracker.auth.AuthService;
import com.dealstracker.domain.User;
import com.dealstracker.domain.WatchedProduct;
import com.dealstracker.repository.ProductRepository;
import com.dealstracker.repository.WatchedProductRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The logged-in user's "interesting" products. */
@RestController
@RequestMapping("/api/me/watchlist")
public class WatchlistController {

    private final AuthService auth;
    private final WatchedProductRepository watched;
    private final ProductRepository products;

    public WatchlistController(AuthService auth, WatchedProductRepository watched, ProductRepository products) {
        this.auth = auth;
        this.watched = watched;
        this.products = products;
    }

    @GetMapping
    public List<UUID> list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return watched.findProductIdsByUserId(auth.requireUser(authorization).getId());
    }

    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@PathVariable UUID productId,
                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        User user = auth.requireUser(authorization);
        if (!products.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product");
        }
        WatchedProduct.Key key = new WatchedProduct.Key(user.getId(), productId);
        if (!watched.existsById(key)) {
            watched.save(new WatchedProduct(user.getId(), productId));
        }
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID productId,
                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        User user = auth.requireUser(authorization);
        watched.deleteById(new WatchedProduct.Key(user.getId(), productId));
    }
}
