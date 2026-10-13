package com.dealstracker.repository;

import com.dealstracker.domain.PriceSnapshot;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, UUID> {

    List<PriceSnapshot> findByListingIdAndScrapedAtAfterOrderByScrapedAtAsc(UUID listingId, OffsetDateTime after);

    List<PriceSnapshot> findByListingIdInAndScrapedAtAfterOrderByScrapedAtAsc(
            Collection<UUID> listingIds, OffsetDateTime after);

    /** Latest snapshot for every listing. */
    @Query("""
            select s from PriceSnapshot s
            join fetch s.listing l
            join fetch l.product
            join fetch l.store
            where s.scrapedAt = (select max(s2.scrapedAt) from PriceSnapshot s2 where s2.listing = s.listing)
            """)
    List<PriceSnapshot> findLatestForAllListings();

    @Query("""
            select s from PriceSnapshot s
            join fetch s.listing l
            join fetch l.store
            where l.product.id = :productId
              and s.scrapedAt = (select max(s2.scrapedAt) from PriceSnapshot s2 where s2.listing = s.listing)
            """)
    List<PriceSnapshot> findLatestForProduct(@Param("productId") UUID productId);

    boolean existsByListingIdAndScrapedAtAfter(UUID listingId, OffsetDateTime after);
}
