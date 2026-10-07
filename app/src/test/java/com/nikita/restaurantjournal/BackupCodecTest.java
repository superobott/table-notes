package com.nikita.restaurantjournal;

import org.json.JSONException;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class BackupCodecTest {
    @Test public void oldBackupsStillRestoreWithoutGoogleLink() throws Exception {
        String json="{\"format\":\"table-notes\",\"version\":1,\"restaurants\":[{\"name\":\"Cafe\",\"grade\":8,\"comment\":\"Good lunch\",\"updated\":1000}]}";
        List<BackupCodec.Entry> entries=BackupCodec.decode(json);
        assertEquals(1,entries.size());assertEquals(8,entries.get(0).grade);assertEquals("",entries.get(0).placeId);
    }
    @Test public void roundTripPreservesUnicodeCommentsBoundaryGradesAndGoogleLinks() throws Exception {
        List<BackupCodec.Entry> original=Arrays.asList(
                new BackupCodec.Entry("מסעדה",10,"Great!\n\"Dessert\" 🍰",1000,"ChIJ-test"),
                new BackupCodec.Entry("Another",0,"",2000,""));
        List<BackupCodec.Entry> restored=BackupCodec.decode(BackupCodec.encode(original));
        assertEquals(2,restored.size());assertEquals(original.get(0).name,restored.get(0).name);
        assertEquals(original.get(0).comment,restored.get(0).comment);assertEquals("ChIJ-test",restored.get(0).placeId);
        assertEquals(0,restored.get(1).grade);assertEquals(2000,restored.get(1).updated);
    }
    @Test public void invalidLaterEntryRejectsWholeBackup() throws Exception {
        String json="{\"format\":\"table-notes\",\"version\":2,\"restaurants\":[{\"name\":\"Valid\",\"grade\":8,\"comment\":\"\",\"updated\":1},{\"name\":\"Invalid\",\"grade\":11,\"comment\":\"\",\"updated\":1}]}";
        try {BackupCodec.decode(json);fail("Invalid backup must be rejected before database changes");} catch(JSONException expected){}
    }
    @Test public void rejectsFractionalAndTextGradesAndUnsupportedVersions() throws Exception {
        for(String grade:Arrays.asList("7.5","-1","11","\"8\"")) {
            String json="{\"format\":\"table-notes\",\"version\":2,\"restaurants\":[{\"name\":\"Cafe\",\"grade\":"+grade+",\"comment\":\"\",\"updated\":1}]}";
            try {BackupCodec.decode(json);fail("Accepted invalid grade "+grade);}catch(JSONException expected){}
        }
        try {BackupCodec.decode("{\"format\":\"table-notes\",\"version\":99,\"restaurants\":[]}");fail("Accepted unknown version");}catch(JSONException expected){}
    }
    @Test public void rejectsInvalidNamesAndNegativeTimestamps() throws Exception {
        for(String entry:Arrays.asList("{\"name\":\"  \",\"grade\":5,\"comment\":\"\",\"updated\":1}","{\"name\":\"Cafe\",\"grade\":5,\"comment\":\"\",\"updated\":-1}")) {
            try {BackupCodec.decode("{\"format\":\"table-notes\",\"version\":2,\"restaurants\":["+entry+"]}");fail("Accepted invalid record");}catch(JSONException expected){}
        }
    }
    @Test public void rejectsCoercedVersionsAndTimestampOverflow() throws Exception {
        for(String version:Arrays.asList("\"2\"","2.25")) {
            try {BackupCodec.decode("{\"format\":\"table-notes\",\"version\":"+version+",\"restaurants\":[]}");fail("Accepted coerced version");}catch(JSONException expected){}
        }
        try {BackupCodec.decode("{\"format\":\"table-notes\",\"version\":2,\"restaurants\":[{\"name\":\"Cafe\",\"grade\":5,\"comment\":\"\",\"updated\":9223372036854775808}]}");fail("Accepted overflowing timestamp");}catch(JSONException expected){}
    }
}
