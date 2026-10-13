package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String barcode;

    @Column(nullable = false)
    private String title;

    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "size_label")
    private String sizeLabel;

    @Column(name = "unit_quantity")
    private BigDecimal unitQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type")
    private UnitType unitType;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Product() {
    }

    public Product(String barcode, String title, Category category) {
        this.barcode = barcode;
        this.title = title;
        this.category = category;
    }

    public UUID getId() { return id; }
    public String getBarcode() { return barcode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public Category getCategory() { return category; }
    public String getSizeLabel() { return sizeLabel; }
    public void setSizeLabel(String sizeLabel) { this.sizeLabel = sizeLabel; }
    public BigDecimal getUnitQuantity() { return unitQuantity; }
    public void setUnitQuantity(BigDecimal unitQuantity) { this.unitQuantity = unitQuantity; }
    public UnitType getUnitType() { return unitType; }
    public void setUnitType(UnitType unitType) { this.unitType = unitType; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
