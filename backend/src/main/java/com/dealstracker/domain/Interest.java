package com.dealstracker.domain;

/** How a user marked a product. */
public enum Interest {
    /** Starred: drives the "cheapest" insights. */
    WATCHED,
    /** Its deals and prices are left out of the user's insights. */
    NOT_INTERESTED
}
