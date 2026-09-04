package com.toolbox.tools.runtime;

public final class PagedQuery {
    public static final int MAX_PAGE_SIZE = 200;

    private final int page;
    private final int pageSize;

    public PagedQuery(int page, int pageSize) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize invalid");
        }
        this.page = page;
        this.pageSize = pageSize;
    }

    public int page() { return page; }
    public int pageSize() { return pageSize; }
    public int offset() {
        long value = (long) page * pageSize;
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("query offset overflow");
        }
        return (int) value;
    }
}
