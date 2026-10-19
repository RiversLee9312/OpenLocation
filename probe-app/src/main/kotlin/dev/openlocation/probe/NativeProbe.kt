package dev.openlocation.probe

/** Independent NDK consumer: links only Android public APIs, never the module implementation. */
object NativeProbe {
    init {
        System.loadLibrary("openlocation_probe")
    }

    external fun queue(durationMs: Int): DoubleArray

    external fun direct(hardwareBuffer: Boolean): DoubleArray
}
