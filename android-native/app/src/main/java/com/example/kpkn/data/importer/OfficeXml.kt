package com.example.kpkn.data.importer

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Piezas comunes de los lectores de ODS y DOCX (ZIP y XML), con las mismas protecciones que [XlsxReader]: tope de bytes
 * descomprimidos, DOM con espacios de nombres y sin DTD ni entidades externas. Los nombres se comparan sin prefijo.
 */
internal object OfficeXml {

    /**
     * Entradas de un ZIP (nombre → bytes) con el tope de [ImportLimits.MAX_UNCOMPRESSED_BYTES]. Devuelve null si los bytes
     * no son un ZIP legible; lanza [ImportFileException] con [tooLarge] si las entradas pasan el tope.
     */
    fun entries(bytes: ByteArray, tooLarge: String): Map<String, ByteArray>? {
        val entries = HashMap<String, ByteArray>()
        var budget = ImportLimits.MAX_UNCOMPRESSED_BYTES
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val content = readBoundedBytes(zip, budget, tooLarge)
                    budget -= content.size
                    entries[entry.name] = content
                }
            }
        } catch (error: IOException) {
            return null
        } catch (error: IllegalArgumentException) {
            // Nombres de entrada mal codificados: el ZIP no se puede leer con seguridad.
            return null
        }
        return entries
    }

    /** DOM con espacios de nombres y sin DTD ni entidades externas. Un XML roto lanza [ImportFileException] con [invalid]. */
    fun parse(bytes: ByteArray, invalid: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        secure(factory)
        return try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (error: Exception) {
            throw ImportFileException(invalid)
        }
    }

    private fun secure(factory: DocumentBuilderFactory) {
        val features = mapOf(
            XMLConstants.FEATURE_SECURE_PROCESSING to true,
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        )
        for ((feature, value) in features) {
            try {
                factory.setFeature(feature, value)
            } catch (error: Exception) {
                // Esta implementación de XML no admite la opción: se siguen aplicando las demás.
            }
        }
    }

    /** Hijos que son elementos, en orden. */
    fun childElements(parent: Node): List<Element> {
        val children = parent.childNodes
        return (0 until children.length).mapNotNull { children.item(it) as? Element }
    }

    /** Nombre local de un elemento, sin prefijo. */
    fun name(element: Element): String = element.localName ?: element.nodeName.substringAfter(':')

    /** Valor de un atributo por su nombre local (sin prefijo), o null si no está. */
    fun attribute(element: Element, localName: String): String? {
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            if ((attribute.localName ?: attribute.nodeName.substringAfter(':')) == localName) return attribute.nodeValue
        }
        return null
    }
}
