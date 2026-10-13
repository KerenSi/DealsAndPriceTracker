package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String chain;

    @Column(name = "branch_name", nullable = false)
    private String branchName;

    @Column(nullable = false)
    private String city;

    private String address;

    @Column(name = "chain_code")
    private String chainCode;

    @Column(name = "store_code")
    private String storeCode;

    /** Words that identify this branch in the chain's Stores file, used to look up store_code. */
    @Column(name = "store_match")
    private String storeMatch;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Store() {
    }

    public Store(String chain, String branchName, String city) {
        this.chain = chain;
        this.branchName = branchName;
        this.city = city;
    }

    public UUID getId() { return id; }
    public String getChain() { return chain; }
    public String getBranchName() { return branchName; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
    public String getChainCode() { return chainCode; }
    public String getStoreCode() { return storeCode; }
    public String getStoreMatch() { return storeMatch; }
    public boolean isEnabled() { return enabled; }

    public void setCodes(String chainCode, String storeCode) {
        this.chainCode = chainCode;
        this.storeCode = storeCode;
    }
}
