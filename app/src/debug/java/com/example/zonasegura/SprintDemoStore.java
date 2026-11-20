package com.example.zonasegura;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class SprintDemoStore extends SQLiteOpenHelper {
    SprintDemoStore(Context context) {
        super(context, "sprint2-demo.db", null, 1);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onConfigure(SQLiteDatabase database) {
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE reports (id INTEGER PRIMARY KEY, author TEXT NOT NULL,"
                + " type TEXT NOT NULL, description TEXT NOT NULL, lat REAL NOT NULL,"
                + " lng REAL NOT NULL, photo TEXT, state TEXT NOT NULL,"
                + " synced INTEGER NOT NULL DEFAULT 0, flagged INTEGER NOT NULL DEFAULT 0)");
        database.execSQL("CREATE TABLE votes (report INTEGER REFERENCES reports(id),"
                + " voter TEXT, confirms INTEGER NOT NULL, PRIMARY KEY (report,voter))");
        database.execSQL("CREATE TABLE sanctions (account TEXT PRIMARY KEY)");
        database.execSQL("CREATE TABLE orders (report INTEGER PRIMARY KEY REFERENCES reports(id))");
        addReport(database, "vecino.ejemplo", "Alumbrado", "Tramo de calle con luminarias apagadas",
                -17.7815, -63.1812, null, "VALIDADO", true);
        addReport(database, "vecina.ejemplo", "Riesgo vial", "Cruce con señalización dañada",
                -17.7834, -63.1803, null, "PENDIENTE_VALIDACION", true);
    }

    @Override public void onUpgrade(SQLiteDatabase database, int from, int to) {
        throw new IllegalStateException("Versión de demostración desconocida");
    }

    long saveReport(String author, String type, String description,
                    double latitude, double longitude, String photo) {
        return addReport(getWritableDatabase(), author, type, description, latitude, longitude,
                photo, "PENDIENTE_VALIDACION", false);
    }

    private long addReport(SQLiteDatabase database, String author, String type,
                           String description, double latitude, double longitude, String photo,
                           String state, boolean synced) {
        ContentValues values = new ContentValues();
        values.put("author", author);
        values.put("type", type);
        values.put("description", description);
        values.put("lat", latitude);
        values.put("lng", longitude);
        values.put("photo", photo);
        values.put("state", state);
        values.put("synced", synced ? 1 : 0);
        return database.insertOrThrow("reports", null, values);
    }

    int simulateSync(String account) {
        ContentValues values = new ContentValues();
        values.put("synced", 1);
        return getWritableDatabase().update("reports", values, "synced=0 AND author=?",
                new String[]{account});
    }

    Cursor reports(String filter, String... args) {
        return getReadableDatabase().query("reports", null, filter, args, null, null, "id DESC");
    }

    boolean isSanctioned(String account) {
        try (Cursor rows = getReadableDatabase().rawQuery(
                "SELECT 1 FROM sanctions WHERE account=?", new String[]{account})) {
            return rows.moveToFirst();
        }
    }

    boolean vote(long report, String account, boolean confirms) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            String reportId = Long.toString(report);
            try (Cursor rows = reports("id=? AND synced=1 AND state='PENDIENTE_VALIDACION'", reportId)) {
                if (!rows.moveToFirst()) return false;
            }
            ContentValues values = new ContentValues();
            values.put("report", report);
            values.put("voter", account);
            values.put("confirms", confirms ? 1 : 0);
            if (database.insertWithOnConflict("votes", null, values, SQLiteDatabase.CONFLICT_IGNORE) < 0) {
                return false;
            }
            if (!confirms) {
                database.execSQL("UPDATE reports SET flagged=1 WHERE id=?", new Object[]{report});
            }
            try (Cursor count = database.rawQuery(
                    "SELECT SUM(confirms), COUNT(*)-SUM(confirms) FROM votes WHERE report=?",
                    new String[]{reportId})) {
                if (count.moveToFirst()) {
                    if (count.getInt(0) >= 2) changeState(report, "VALIDADO", "PENDIENTE_VALIDACION");
                    if (count.getInt(1) >= 2) changeState(report, "RECHAZADO", "PENDIENTE_VALIDACION");
                }
            }
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    boolean assignResource(long report) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            try (Cursor rows = reports("state='EN_ATENCION'")) {
                if (rows.moveToFirst()) return false;
            }
            if (!changeState(report, "EN_ATENCION", "VALIDADO")) return false;
            ContentValues order = new ContentValues();
            order.put("report", report);
            database.insertOrThrow("orders", null, order);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    boolean changeState(long report, String state, String previous) {
        ContentValues values = new ContentValues();
        values.put("state", state);
        return getWritableDatabase().update("reports", values, "id=? AND synced=1 AND state=?",
                new String[]{Long.toString(report), previous}) == 1;
    }

    boolean sanction(long report, String account) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try (Cursor rows = reports("id=? AND author=? AND synced=1 AND state='RECHAZADO'",
                Long.toString(report), account)) {
            if (!rows.moveToFirst()) return false;
            ContentValues values = new ContentValues();
            values.put("account", account);
            database.insertWithOnConflict("sanctions", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }
}
