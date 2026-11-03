package org.amrosman.jsmartclinic.util;

/** Utility for simple in-memory pagination. */
public final class Paging {

    private Paging() {
    }

    public static int offset(int page, int pageSize) {
        return Math.max(0, page) * pageSize;
    }

    public static int pageCount(int total, int pageSize) {
        return Math.max(1, (int) Math.ceil(total / (double) pageSize));
    }
}
