package dev.openlocation.app.data.recording

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.os.SystemClock
import dev.openlocation.runtime.Gpx
import dev.openlocation.runtime.RecordedFix
import dev.openlocation.runtime.RecordedTrackGpx
import dev.openlocation.scenario.GeoPoint
import java.io.OutputStream

data class TrackSummary(
    val id: Long,
    val started: Long,
    val ended: Long?,
    val status: String,
    val points: Int,
    val distance: Double,
    val kind: String = "recording",
    val name: String = "",
) {
    val active
        get() = status == "recording"
}

/** Private, transactional storage. Incomplete sessions survive process death and are exportable. */
class TrackRepository private constructor(context: Context) :
    SQLiteOpenHelper(context, "recorded-tracks.db", null, 2) {
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL(
            "UPDATE tracks SET status='interrupted', ended=COALESCE((SELECT MAX(utc) FROM fixes WHERE track=tracks.id),started) WHERE status='recording'"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tracks(id INTEGER PRIMARY KEY, started INTEGER NOT NULL, ended INTEGER, status TEXT NOT NULL, points INTEGER NOT NULL DEFAULT 0, distance REAL NOT NULL DEFAULT 0, kind TEXT NOT NULL DEFAULT 'recording', name TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE fixes(track INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE, seq INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, alt REAL, accuracy REAL NOT NULL, utc INTEGER NOT NULL, elapsed INTEGER NOT NULL, PRIMARY KEY(track,seq))"
        )
        db.execSQL(
            "CREATE TABLE documents(track INTEGER PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE, original BLOB NOT NULL)"
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2)
        db.execSQL("ALTER TABLE tracks ADD COLUMN kind TEXT NOT NULL DEFAULT 'recording'")
        db.execSQL("ALTER TABLE tracks ADD COLUMN name TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE documents(track INTEGER PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE, original BLOB NOT NULL)"
        )
    }

    @Synchronized
    fun list(): List<TrackSummary> =
        readableDatabase
            .rawQuery(
                "SELECT id,started,ended,status,points,distance,kind,name FROM tracks ORDER BY id DESC",
                null,
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        TrackSummary(
                            c.getLong(0),
                            c.getLong(1),
                            if (c.isNull(2)) null else c.getLong(2),
                            c.getString(3),
                            c.getInt(4),
                            c.getDouble(5),
                            c.getString(6),
                            c.getString(7),
                        )
                    )
                }
            }

    /** Called once by the service before beginning a new explicit recording. */
    @Synchronized
    fun begin(): Long {
        writableDatabase.execSQL(
            "UPDATE tracks SET status='interrupted', ended=? WHERE status='recording'",
            arrayOf(System.currentTimeMillis()),
        )
        return writableDatabase.insertOrThrow(
            "tracks",
            null,
            ContentValues().apply {
                put("started", System.currentTimeMillis())
                put("status", "recording")
            },
        )
    }

    @Synchronized
    fun recover() {
        writableDatabase.execSQL(
            "UPDATE tracks SET status='interrupted', ended=COALESCE((SELECT MAX(utc) FROM fixes WHERE track=tracks.id),started) WHERE status='recording'"
        )
    }

    @Synchronized
    fun finish(id: Long, status: String = "saved") {
        require(
            status in setOf("saved", "interrupted", "permission", "provider", "limit", "storage")
        )
        writableDatabase.update(
            "tracks",
            ContentValues().apply {
                put("status", status)
                put("ended", System.currentTimeMillis())
            },
            "id=? AND status='recording'",
            arrayOf(id.toString()),
        )
    }

    /**
     * Mock/stale/duplicate fixes never enter the database. The controller UID is not a simulation
     * target.
     */
    @Synchronized
    fun append(id: Long, location: Location): Boolean {
        val summary = list().firstOrNull { it.id == id && it.active } ?: return false
        val previous =
            readableDatabase
                .rawQuery(
                    "SELECT lat,lon,elapsed FROM fixes WHERE track=? ORDER BY seq DESC LIMIT 1",
                    arrayOf(id.toString()),
                )
                .use { c ->
                    if (c.moveToFirst()) Triple(c.getDouble(0), c.getDouble(1), c.getLong(2))
                    else null
                }
        val fix =
            RecordedFix(
                location.latitude,
                location.longitude,
                if (location.hasAltitude()) location.altitude else null,
                location.accuracy,
                location.time,
                location.elapsedRealtimeNanos,
            )
        if (
            !location.hasAccuracy() ||
                !fix.isUsable(
                    SystemClock.elapsedRealtimeNanos(),
                    previous?.third ?: 0,
                    location.isMock,
                )
        )
            return false
        if (
            summary.points >= 43_200 ||
                System.currentTimeMillis() - summary.started >= 12 * 60 * 60 * 1000L
        ) {
            finish(id, "limit")
            return false
        }
        val meters = FloatArray(1)
        if (previous != null)
            Location.distanceBetween(
                previous.first,
                previous.second,
                fix.latitude,
                fix.longitude,
                meters,
            )
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow(
                "fixes",
                null,
                ContentValues().apply {
                    put("track", id)
                    put("seq", summary.points)
                    put("lat", fix.latitude)
                    put("lon", fix.longitude)
                    if (fix.altitude == null) putNull("alt") else put("alt", fix.altitude)
                    put("accuracy", fix.accuracy)
                    put("utc", fix.utcMillis)
                    put("elapsed", fix.elapsedNanos)
                },
            )
            db.execSQL(
                "UPDATE tracks SET points=points+1,distance=distance+? WHERE id=?",
                arrayOf<Any>(meters[0].toDouble(), id),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    @Synchronized
    fun export(id: Long, output: OutputStream) {
        check(list().any { it.id == id && !it.active && it.points > 0 })
        val originalSize =
            readableDatabase
                .rawQuery(
                    "SELECT length(original) FROM documents WHERE track=?",
                    arrayOf(id.toString()),
                )
                .use { c -> if (c.moveToFirst()) c.getInt(0) else null }
        if (originalSize != null) {
            for (offset in 0 until originalSize step 65536) readableDatabase
                .rawQuery(
                    "SELECT substr(original,?,65536) FROM documents WHERE track=?",
                    arrayOf((offset + 1).toString(), id.toString()),
                )
                .use { c ->
                    check(c.moveToFirst())
                    output.write(c.getBlob(0))
                }
            return
        }
        val writer = output.bufferedWriter()
        readableDatabase
            .rawQuery(
                "SELECT lat,lon,alt,accuracy,utc,elapsed FROM fixes WHERE track=? ORDER BY seq",
                arrayOf(id.toString()),
            )
            .use { c ->
                RecordedTrackGpx.write(
                    writer,
                    sequence {
                        while (c.moveToNext()) yield(
                            RecordedFix(
                                c.getDouble(0),
                                c.getDouble(1),
                                if (c.isNull(2)) null else c.getDouble(2),
                                c.getFloat(3),
                                c.getLong(4),
                                c.getLong(5),
                            )
                        )
                    },
                )
            }
        writer.flush()
    }

    /** Original GPX bytes are retained atomically with the overview; re-export is lossless. */
    @Synchronized
    fun importGpx(bytes: ByteArray, name: String, kind: String = "imported"): Long {
        require(bytes.size <= 8 * 1024 * 1024 && kind in setOf("imported", "saved"))
        val points = Gpx.read(bytes.inputStream())
        val distance =
            points.zipWithNext().sumOf { (a, b) ->
                FloatArray(1)
                    .also {
                        Location.distanceBetween(
                            a.latitude,
                            a.longitude,
                            b.latitude,
                            b.longitude,
                            it,
                        )
                    }[0]
                    .toDouble()
            }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val id =
                db.insertOrThrow(
                    "tracks",
                    null,
                    ContentValues().apply {
                        put("started", now)
                        put("ended", now)
                        put("status", "saved")
                        put("points", points.size)
                        put("distance", distance)
                        put("kind", kind)
                        put("name", cleanName(name))
                    },
                )
            db.insertOrThrow(
                "documents",
                null,
                ContentValues().apply {
                    put("track", id)
                    put("original", bytes)
                },
            )
            points.forEachIndexed { index, p ->
                db.insertOrThrow(
                    "fixes",
                    null,
                    ContentValues().apply {
                        put("track", id)
                        put("seq", index)
                        put("lat", p.latitude)
                        put("lon", p.longitude)
                        put("alt", p.altitude)
                        put("accuracy", 0)
                        put("utc", 0)
                        put("elapsed", 0)
                    },
                )
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun rename(id: Long, name: String) {
        writableDatabase.update(
            "tracks",
            ContentValues().apply { put("name", cleanName(name)) },
            "id=?",
            arrayOf(id.toString()),
        )
    }

    /** Simulation is bounded to 10,000 points; the library/export always retain the full track. */
    @Synchronized
    fun scenePoints(id: Long): List<GeoPoint> {
        val summary = list().first { it.id == id && !it.active }
        val stride = maxOf(1, (summary.points + 9998) / 9999)
        return readableDatabase
            .rawQuery(
                "SELECT lat,lon,alt FROM fixes WHERE track=? AND (seq % ?=0 OR seq=?) ORDER BY seq",
                arrayOf(id.toString(), stride.toString(), (summary.points - 1).toString()),
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        GeoPoint(
                            c.getDouble(0),
                            c.getDouble(1),
                            if (c.isNull(2)) 0.0 else c.getDouble(2),
                        )
                    )
                }
            }
    }

    private fun cleanName(name: String) =
        name
            .filterNot { it.isISOControl() }
            .trim()
            .also { require(it.isNotEmpty() && it.length <= 120) }

    /** Bounded overview for rendering; export always retains every original observation. */
    @Synchronized
    fun preview(id: Long, count: Int): List<RecordedFix> {
        val stride = maxOf(1, (count + 799) / 800)
        return readableDatabase
            .rawQuery(
                "SELECT lat,lon,alt,accuracy,utc,elapsed FROM fixes WHERE track=? AND (seq % ?=0 OR seq=?) ORDER BY seq",
                arrayOf(id.toString(), stride.toString(), (count - 1).toString()),
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        RecordedFix(
                            c.getDouble(0),
                            c.getDouble(1),
                            if (c.isNull(2)) null else c.getDouble(2),
                            c.getFloat(3),
                            c.getLong(4),
                            c.getLong(5),
                        )
                    )
                }
            }
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("tracks", "id=? AND status!='recording'", arrayOf(id.toString()))
    }

    companion object {
        @Volatile private var instance: TrackRepository? = null

        fun get(context: Context) =
            instance
                ?: synchronized(this) {
                    instance ?: TrackRepository(context.applicationContext).also { instance = it }
                }
    }
}
