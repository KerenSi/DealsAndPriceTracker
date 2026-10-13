package com.dealstracker.repository;

import com.dealstracker.domain.Category;
import com.dealstracker.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByBarcode(String barcode);

    @Query("""
            select p from Product p
            where (:category is null or p.category = :category)
              and (:q is null
                   or lower(p.title) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.brand) like lower(concat('%', cast(:q as string), '%')))
            order by p.category, p.brand, p.title
            """)
    List<Product> search(@Param("category") Category category, @Param("q") String q);
}
