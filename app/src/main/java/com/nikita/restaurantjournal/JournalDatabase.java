package com.nikita.restaurantjournal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class JournalDatabase extends SQLiteOpenHelper {
    JournalDatabase(Context context) { this(context, "restaurants.db"); }
    // Tests use a separate database; never open or delete the user's journal.
    JournalDatabase(Context context, String name) { super(context, name, null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE places (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, grade INTEGER NOT NULL CHECK(grade BETWEEN 0 AND 10), comment TEXT NOT NULL, updated INTEGER NOT NULL, google_place_id TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int old, int next) {
        if (old < 2) db.execSQL("ALTER TABLE places ADD COLUMN google_place_id TEXT");
    }
}
