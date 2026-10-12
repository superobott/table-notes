package com.nikita.restaurantjournal;

import java.util.Map;

/** Exact place-ID lookup. Empty IDs never identify a journal entry. */
final class PlaceIdLookup {
    static long findEntryId(String placeId, Map<Long, String> entries) {
        if (placeId == null || placeId.isEmpty()) return -1;
        for (Map.Entry<Long, String> entry : entries.entrySet()) {
            if (placeId.equals(entry.getValue())) return entry.getKey();
        }
        return -1;
    }
}
