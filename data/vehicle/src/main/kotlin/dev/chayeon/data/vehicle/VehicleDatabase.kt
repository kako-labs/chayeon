package dev.chayeon.data.vehicle

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class VehicleDatabase(context: Context, name: String = "drive-signature.db") : SQLiteOpenHelper(context, name, null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE accounts (account_key TEXT PRIMARY KEY, revision INTEGER NOT NULL, signature TEXT NOT NULL)")
        db.execSQL("CREATE TABLE trips (account_key TEXT NOT NULL REFERENCES accounts(account_key) ON DELETE CASCADE, id TEXT NOT NULL, ended_at INTEGER NOT NULL, derived_metrics TEXT NOT NULL, PRIMARY KEY(account_key,id))")
        db.execSQL("CREATE TABLE recaps (account_key TEXT NOT NULL REFERENCES accounts(account_key) ON DELETE CASCADE, id TEXT NOT NULL, ended_at INTEGER NOT NULL, recap TEXT NOT NULL, PRIMARY KEY(account_key,id))")
        db.execSQL("CREATE TABLE commands (account_key TEXT NOT NULL REFERENCES accounts(account_key) ON DELETE CASCADE, id TEXT NOT NULL, created_at INTEGER NOT NULL, fingerprint TEXT NOT NULL, result TEXT NOT NULL, PRIMARY KEY(account_key,id))")
        db.execSQL("CREATE INDEX trip_retention ON trips(ended_at)")
        db.execSQL("CREATE INDEX recap_retention ON recaps(ended_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("An explicit derived-data migration is required")
    }
}
