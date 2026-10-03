package com.acme.blueprint.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Allow-list between the public {@code ?sort=} vocabulary and entity property
 * paths. Clients sort by stable, documented keys; the entity can be renamed or
 * reshaped without breaking them, and nobody can sort by an unindexed column.
 * A unique tiebreaker is always appended, so offset paging never repeats or
 * skips rows that share a sort value.
 */
public final class SortMapping {

    private final Map<String, String> propertyByKey;
    private final Sort.Order tiebreaker;

    private SortMapping(Map<String, String> propertyByKey, String tiebreakerProperty) {
        this.propertyByKey = Map.copyOf(propertyByKey);
        this.tiebreaker = Sort.Order.asc(tiebreakerProperty);
    }

    /**
     * @param propertyByKey      public sort key → entity property path
     * @param tiebreakerProperty a unique property, appended last
     */
    public static SortMapping of(Map<String, String> propertyByKey, String tiebreakerProperty) {
        return new SortMapping(propertyByKey, tiebreakerProperty);
    }

    /** @throws InvalidSortException if the request sorts by a key that is not allowed */
    public Pageable translate(Pageable requested) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : requested.getSort()) {
            String property = propertyByKey.get(order.getProperty());
            if (property == null) {
                throw new InvalidSortException(order.getProperty(), keys());
            }
            orders.add(order.withProperty(property));
        }
        orders.add(tiebreaker);
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), Sort.by(orders));
    }

    public Set<String> keys() {
        return propertyByKey.keySet();
    }
}
