package app.dongyu.transfer

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

internal object Xml {
    fun parse(input: InputStream): Document {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        return try {
            f.newDocumentBuilder().parse(input)
        } catch (e: Exception) {
            throw ImportFormatException("Malformed XML: ${e.message}")
        }
    }

    fun Element.children(local: String): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) {
            if (n.nodeType == Node.ELEMENT_NODE && (n.localName ?: n.nodeName.substringAfter(':')) == local) out += n as Element
            n = n.nextSibling
        }
        return out
    }

    fun Element.child(local: String): Element? = children(local).firstOrNull()
    fun Element.childText(local: String): String? = child(local)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    /** Depth-first search for the first descendant with the local name, any namespace. */
    fun Element.find(local: String): Element? {
        val l = getElementsByTagNameNS("*", local)
        return if (l.length > 0) l.item(0) as Element else null
    }

    fun Document.all(local: String): List<Element> {
        val l = getElementsByTagNameNS("*", local)
        return (0 until l.length).map { l.item(it) as Element }
    }

    fun time(s: String?): Long? = s?.let {
        runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(it).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    fun iso(ms: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms - ms % 1000))

    fun esc(s: String) = buildString {
        s.forEach { c ->
            when (c) {
                '<' -> append("&lt;"); '>' -> append("&gt;"); '&' -> append("&amp;")
                '"' -> append("&quot;"); '\'' -> append("&apos;")
                else -> if (c.code >= 0x20 || c == '\n' || c == '\t') append(c)
            }
        }
    }

    fun num(v: Double, digits: Int): String = java.math.BigDecimal(v).setScale(digits, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
