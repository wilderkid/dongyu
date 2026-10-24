package app.dongyu.transfer

/** Format-agnostic entry points used by the importer, exporter and tests. */
object ActivityCodec {
    fun parse(bytes: ByteArray, name: String?): List<ActivityData> {
        val format = FileFormat.detect(bytes.copyOfRange(0, minOf(bytes.size, 512)), name)
            ?: throw ImportFormatException("Unsupported file")
        val list = when (format) {
            FileFormat.GPX -> GpxCodec.parse(bytes.inputStream())
            FileFormat.TCX -> TcxCodec.parse(bytes.inputStream())
            FileFormat.FIT -> listOf(FitCodec.parse(bytes.inputStream()))
        }
        return list.filter { it.points.isNotEmpty() || (it.durationSec ?: 0) > 0 || it.endTime > it.startTime }
            .ifEmpty { throw ImportFormatException("No activity found") }
    }

    fun write(a: ActivityData, format: FileFormat): ByteArray = when (format) {
        FileFormat.GPX -> GpxCodec.write(a).toByteArray()
        FileFormat.TCX -> TcxCodec.write(a).toByteArray()
        FileFormat.FIT -> FitCodec.write(a)
    }
}
