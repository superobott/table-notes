package com.nikita.restaurantjournal;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public class PlaceIdLookupTest {
    private Map<Long,String> entries() {
        Map<Long,String> entries=new LinkedHashMap<>();
        entries.put(1L,null); entries.put(2L,"");
        entries.put(3L,"Place-A"); entries.put(4L,"Place-B"); entries.put(5L,"Place-A");
        return entries;
    }
    @Test public void returnsExistingIdWithoutCreatingAnotherEntry() {
        Map<Long,String> entries=entries();
        assertEquals(3L,PlaceIdLookup.findEntryId("Place-A",entries));
        assertEquals(4L,PlaceIdLookup.findEntryId("Place-B",entries));
        assertEquals(5,entries.size());
    }
    @Test public void missingOrEmptyIdDoesNotMatchUnlinkedRows() {
        assertEquals(-1L,PlaceIdLookup.findEntryId(null,entries()));
        assertEquals(-1L,PlaceIdLookup.findEntryId("",entries()));
        assertEquals(-1L,PlaceIdLookup.findEntryId("missing",entries()));
        assertEquals(-1L,PlaceIdLookup.findEntryId("Place-A",new LinkedHashMap<>()));
    }
    @Test public void idsAreExactAndFirstExistingMatchWins() {
        assertEquals(-1L,PlaceIdLookup.findEntryId("place-a",entries()));
        assertEquals(-1L,PlaceIdLookup.findEntryId("Place-A ",entries()));
        assertEquals(3L,PlaceIdLookup.findEntryId("Place-A",entries()));
    }
}
