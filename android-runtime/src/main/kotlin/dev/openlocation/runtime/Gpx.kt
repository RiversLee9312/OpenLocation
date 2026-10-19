package dev.openlocation.runtime

import android.util.Xml
import dev.openlocation.scenario.GeoPoint
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser

/** Streaming GPX reader. External entities are disabled; input and point counts are bounded. */
object Gpx {
    fun read(input: InputStream): List<GeoPoint> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(input.bufferedReader())
        val points = mutableListOf<GeoPoint>()
        var lat: Double? = null
        var lon: Double? = null
        var altitude = 0.0
        var pointTag: String? = null
        var events = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            require(++events <= 500000) { "GPX too large" }
            if (parser.eventType == XmlPullParser.START_TAG) {
                require(parser.name != "!DOCTYPE")
                when (parser.name) {
                    "trkpt",
                    "rtept" -> {
                        pointTag = parser.name
                        lat = parser.getAttributeValue(null, "lat").toDouble()
                        lon = parser.getAttributeValue(null, "lon").toDouble()
                        altitude = 0.0
                    }
                    "ele" -> if (pointTag != null) altitude = parser.nextText().toDouble()
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == pointTag) {
                points += GeoPoint(requireNotNull(lat), requireNotNull(lon), altitude)
                require(points.size <= 10000)
                pointTag = null
            }
            parser.next()
        }
        require(points.size >= 2) { "GPX needs at least two points" }
        return points
    }

    fun write(points: List<GeoPoint>): String = buildString {
        append(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"OpenLocation\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>"
        )
        points.forEach {
            append(
                "<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\"><ele>${it.altitude}</ele></trkpt>"
            )
        }
        append("</trkseg></trk></gpx>")
    }
}
