package com.nikita.restaurantjournal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Versioned journal-only backups; API keys and live provider content are excluded. */
final class BackupCodec {
    static final class Entry {
        final String name, comment, placeId;
        final int grade;
        final long updated;
        Entry(String name,int grade,String comment,long updated,String placeId) {
            this.name=name;this.grade=grade;this.comment=comment;this.updated=updated;this.placeId=placeId;
        }
    }

    static List<Entry> decode(String content) throws JSONException {
        JSONObject document = new JSONObject(content);
        int version = document.getInt("version");
        if (!(document.get("version") instanceof Number) || document.getDouble("version")!=version
                || !"table-notes".equals(document.getString("format")) || (version!=1 && version!=2)) throw new JSONException("Unsupported format");
        JSONArray rows = document.getJSONArray("restaurants");
        if (rows.length()>10000) throw new JSONException("Too many entries");
        List<Entry> entries = new ArrayList<>();
        for (int index=0;index<rows.length();index++) {
            JSONObject row = rows.getJSONObject(index);
            if (!(row.get("name") instanceof String) || !(row.get("comment") instanceof String)
                    || !(row.get("grade") instanceof Number) || !(row.get("updated") instanceof Number)) throw new JSONException("Wrong field type");
            String name=row.getString("name").trim(), comment=row.getString("comment");
            double grade=row.getDouble("grade");
            long timestamp;
            try {timestamp=new java.math.BigDecimal(row.get("updated").toString()).longValueExact();}
            catch(ArithmeticException|NumberFormatException invalid){throw new JSONException("Invalid timestamp");}
            String placeId="";
            if (version==2 && row.has("google_place_id") && !row.isNull("google_place_id")) {
                if (!(row.get("google_place_id") instanceof String)) throw new JSONException("Invalid place ID");
                placeId=row.getString("google_place_id");
            }
            if (name.isEmpty() || name.length()>120 || comment.length()>1000 || placeId.length()>1024
                    || !Double.isFinite(grade) || grade<0 || grade>10 || grade!=Math.floor(grade)
                    || timestamp<0) throw new JSONException("Invalid entry");
            entries.add(new Entry(name,(int)grade,comment,timestamp,placeId));
        }
        return entries;
    }

    static String encode(List<Entry> entries) throws JSONException {
        JSONArray rows=new JSONArray();
        for (Entry entry:entries) {
            JSONObject row=new JSONObject().put("name",entry.name).put("grade",entry.grade)
                    .put("comment",entry.comment).put("updated",entry.updated);
            if (!entry.placeId.isEmpty()) row.put("google_place_id",entry.placeId);
            rows.put(row);
        }
        return new JSONObject().put("format","table-notes").put("version",2).put("restaurants",rows).toString(2);
    }
}
