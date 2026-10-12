package com.nikita.restaurantjournal;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class JournalDatabaseMigrationTest {
    @Test public void versionOneUpgradePreservesEveryRowAndAcceptsPlaceIds() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="migration-test-"+System.nanoTime()+".db";
        try {
            try(SQLiteDatabase old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)) {
                old.execSQL("CREATE TABLE places (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, grade INTEGER NOT NULL CHECK(grade BETWEEN 0 AND 10), comment TEXT NOT NULL, updated INTEGER NOT NULL)");
                old.execSQL("INSERT INTO places VALUES (7, 'First meal', 0, 'Original comment', 123)");
                old.execSQL("INSERT INTO places VALUES (12, 'Second meal', 10, '', 456)");
                old.setVersion(1);
            }
            try(JournalDatabase helper=new JournalDatabase(context,name)) {
                SQLiteDatabase upgraded=helper.getWritableDatabase();
                assertEquals(2,upgraded.getVersion());
                try(Cursor rows=upgraded.rawQuery("SELECT id,name,grade,comment,updated,google_place_id FROM places ORDER BY id",null)) {
                    assertEquals(2,rows.getCount());
                    assertTrue(rows.moveToNext());
                    assertEquals(7,rows.getLong(0)); assertEquals("First meal",rows.getString(1));
                    assertEquals(0,rows.getInt(2)); assertEquals("Original comment",rows.getString(3));
                    assertEquals(123,rows.getLong(4)); assertTrue(rows.isNull(5));
                    assertTrue(rows.moveToNext());
                    assertEquals(12,rows.getLong(0)); assertEquals("Second meal",rows.getString(1));
                    assertEquals(10,rows.getInt(2)); assertEquals("",rows.getString(3));
                    assertEquals(456,rows.getLong(4)); assertTrue(rows.isNull(5));
                    assertFalse(rows.moveToNext());
                }
                upgraded.execSQL("INSERT INTO places (name,grade,comment,updated,google_place_id) VALUES ('New meal',8,'My note',789,'place-id')");
            }
            try(JournalDatabase reopened=new JournalDatabase(context,name);
                Cursor rows=reopened.getReadableDatabase().rawQuery("SELECT id,google_place_id FROM places ORDER BY id",null)) {
                assertEquals(3,rows.getCount());
                assertTrue(rows.moveToLast()); assertTrue(rows.getLong(0)>12);
                assertEquals("place-id",rows.getString(1));
            }
        } finally { context.deleteDatabase(name); }
    }
}
