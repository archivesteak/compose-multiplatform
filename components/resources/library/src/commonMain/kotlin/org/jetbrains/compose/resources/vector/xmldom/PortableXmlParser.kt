package org.jetbrains.compose.resources.vector.xmldom

/**
 * Portable XML parser used by targets whose standard library does not provide a DOM parser.
 *
 * Every other tier borrows a platform parser (NSXMLParser on Apple, SAX/DOM on JVM and Android,
 * DOMParser on the web). The Kotlin/Native Windows klib ships no XML parser, and vector drawables
 * are machine-generated XML with a narrow grammar — elements, attributes, namespaces, text,
 * comments, CDATA and a prolog — so this parses that grammar directly. Input outside the grammar
 * (a DOCTYPE with an internal subset, unknown entities) fails with [MalformedXMLException]
 * rather than guessing.
 */
internal fun parsePortableXml(xml: String): Element = PortableXmlParser(xml).parseDocument()

private class PortableElementImpl(
    override val localName: String,
    override val nodeName: String,
    override val namespaceURI: String,
    /** URI -> prefix, as declared at the point this element started. */
    val prefixMap: Map<String, String>,
    val attributes: Map<String, String>,
    val attributesByNamespace: Map<Pair<String, String>, String>,
    /** Prefix -> previous URI for namespace declarations restored when this element ends. */
    val previousNamespaceMappings: Map<String, String?>,
) : Element {
    override var textContent: String? = null
    val children = mutableListOf<PortableElementImpl>()

    override val childNodes: NodeList
        get() = object : NodeList {
            override fun item(i: Int): Node = children[i]
            override val length: Int
                get() = children.size
        }

    override fun getAttributeNS(nameSpaceURI: String, localName: String): String {
        return attributesByNamespace[nameSpaceURI to localName].orEmpty()
    }

    override fun getAttribute(name: String): String = attributes[name] ?: ""

    override fun lookupPrefix(namespaceURI: String): String = prefixMap[namespaceURI] ?: ""
}

private class PortableXmlParser(private val xml: String) {
    private var pos = 0
    private var root: PortableElementImpl? = null
    private val stack = ArrayDeque<PortableElementImpl>()
    private val prefixToUri = mutableMapOf(
        "xml" to "http://www.w3.org/XML/1998/namespace"
    )

    fun parseDocument(): Element {
        while (pos < xml.length) {
            when {
                xml.startsWith("<?", pos) -> skipPast("?>")
                xml.startsWith("<!--", pos) -> skipPast("-->")
                xml.startsWith("<![CDATA[", pos) -> readCData()
                xml.startsWith("</", pos) -> readEndTag()
                xml.startsWith("<!", pos) ->
                    throw MalformedXMLException("unsupported declaration at offset $pos")
                xml[pos] == '<' -> readStartTag()
                else -> readText()
            }
        }
        stack.lastOrNull()?.let { throw MalformedXMLException("unclosed element <${it.nodeName}>") }
        return root ?: throw MalformedXMLException("no root element")
    }

    private fun readStartTag() {
        pos++ // consume '<'
        val nodeName = readName()
        val attributes = LinkedHashMap<String, String>()
        val selfClosing: Boolean
        while (true) {
            skipWhitespace()
            when {
                pos >= xml.length ->
                    throw MalformedXMLException("unexpected end of file inside <$nodeName>")
                xml.startsWith("/>", pos) -> {
                    pos += 2
                    selfClosing = true
                    break
                }
                xml[pos] == '>' -> {
                    pos++
                    selfClosing = false
                    break
                }
                else -> {
                    val attrName = readName()
                    skipWhitespace()
                    expect('=')
                    skipWhitespace()
                    val quote = xml.getOrNull(pos)
                    if (quote != '"' && quote != '\'') {
                        throw MalformedXMLException("expected a quoted value for '$attrName'")
                    }
                    pos++
                    val value = decodeEntities(readUntil(quote))
                    if (attributes.put(attrName, value) != null) {
                        throw MalformedXMLException("duplicate attribute '$attrName' in <$nodeName>")
                    }
                }
            }
        }

        // Namespace declarations apply to this element itself, so register them first.
        val previousMappings = linkedMapOf<String, String?>()
        for ((name, value) in attributes) {
            val prefix = when {
                name == "xmlns" -> ""
                name.startsWith("xmlns:") -> name.substring(6)
                else -> continue
            }
            previousMappings[prefix] = prefixToUri[prefix]
            prefixToUri[prefix] = value
        }

        val attributesByNamespace = LinkedHashMap<Pair<String, String>, String>()
        for ((name, value) in attributes) {
            if (name == "xmlns" || name.startsWith("xmlns:")) continue
            val attributePrefix = name.substringBefore(':', "")
            val attributeNamespace = if (attributePrefix.isEmpty()) {
                "" // A default namespace never applies to an unprefixed attribute.
            } else {
                prefixToUri[attributePrefix]
                    ?: throw MalformedXMLException("undeclared prefix '$attributePrefix' on attribute '$name'")
            }
            val expandedName = attributeNamespace to name.substringAfter(':')
            if (attributesByNamespace.put(expandedName, value) != null) {
                throw MalformedXMLException("duplicate namespaced attribute '$name' in <$nodeName>")
            }
        }

        val prefix = nodeName.substringBefore(':', "")
        if (prefix.isNotEmpty() && prefix !in prefixToUri) {
            throw MalformedXMLException("undeclared prefix '$prefix' on element <$nodeName>")
        }
        val element = PortableElementImpl(
            localName = nodeName.substringAfter(':'),
            nodeName = nodeName,
            namespaceURI = prefixToUri[prefix].orEmpty(),
            prefixMap = prefixToUri.entries.associateBy({ it.value }, { it.key }),
            attributes = attributes,
            attributesByNamespace = attributesByNamespace,
            previousNamespaceMappings = previousMappings,
        )
        if (stack.isEmpty() && root != null) {
            throw MalformedXMLException("more than one root element")
        }
        if (root == null) root = element
        stack.lastOrNull()?.children?.add(element)
        if (selfClosing) {
            restoreNamespaceMappings(previousMappings)
        } else {
            stack.addLast(element)
        }
    }

    private fun readEndTag() {
        pos += 2 // consume "</"
        val name = readName()
        skipWhitespace()
        expect('>')
        val element = stack.removeLastOrNull()
            ?: throw MalformedXMLException("unexpected closing tag </$name>")
        if (element.nodeName != name) {
            throw MalformedXMLException("expected </${element.nodeName}> but found </$name>")
        }
        restoreNamespaceMappings(element.previousNamespaceMappings)
    }

    private fun readText() {
        val start = pos
        while (pos < xml.length && xml[pos] != '<') pos++
        val text = decodeEntities(xml.substring(start, pos))
        if (stack.isEmpty() && text.isNotBlank()) {
            throw MalformedXMLException("text outside the root element")
        }
        appendText(text)
    }

    private fun readCData() {
        pos += "<![CDATA[".length
        val end = xml.indexOf("]]>", pos)
        if (end < 0) throw MalformedXMLException("unterminated CDATA section")
        if (stack.isEmpty()) throw MalformedXMLException("CDATA outside the root element")
        appendText(xml.substring(pos, end))
        pos = end + 3
    }

    private fun appendText(text: String) {
        val current = stack.lastOrNull() ?: return
        current.textContent = current.textContent.orEmpty() + text
    }

    private fun restoreNamespaceMappings(previousMappings: Map<String, String?>) {
        for ((prefix, previousUri) in previousMappings) {
            if (previousUri == null) prefixToUri.remove(prefix)
            else prefixToUri[prefix] = previousUri
        }
    }

    private fun readName(): String {
        val start = pos
        while (
            pos < xml.length &&
            !xml[pos].isWhitespace() &&
            xml[pos] !in "=/>?<\"'&"
        ) {
            pos++
        }
        if (pos == start) throw MalformedXMLException("expected a name at offset $pos")
        return xml.substring(start, pos).also { name ->
            val parts = name.split(':')
            if (parts.size > 2 || parts.any { !it.isXmlNamePart() }) {
                throw MalformedXMLException("invalid name '$name' at offset $start")
            }
        }
    }

    private fun readUntil(delimiter: Char): String {
        val end = xml.indexOf(delimiter, pos)
        if (end < 0) throw MalformedXMLException("unterminated string at offset $pos")
        val result = xml.substring(pos, end)
        pos = end + 1
        return result
    }

    private fun skipPast(marker: String) {
        val end = xml.indexOf(marker, pos)
        if (end < 0) throw MalformedXMLException("unterminated markup at offset $pos")
        pos = end + marker.length
    }

    private fun skipWhitespace() {
        while (pos < xml.length && xml[pos].isWhitespace()) pos++
    }

    private fun expect(c: Char) {
        if (xml.getOrNull(pos) != c) {
            throw MalformedXMLException("expected '$c' at offset $pos")
        }
        pos++
    }

    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        return buildString(s.length) {
            var i = 0
            while (i < s.length) {
                if (s[i] != '&') {
                    append(s[i++])
                    continue
                }
                val end = s.indexOf(';', i + 1)
                if (end < 0) throw MalformedXMLException("unterminated entity at offset $i")
                when (val entity = s.substring(i + 1, end)) {
                    "amp" -> append('&')
                    "lt" -> append('<')
                    "gt" -> append('>')
                    "quot" -> append('"')
                    "apos" -> append('\'')
                    else -> {
                        val codePoint = when {
                            entity.startsWith("#x", ignoreCase = true) ->
                                entity.substring(2).toIntOrNull(16)
                            entity.startsWith("#") ->
                                entity.substring(1).toIntOrNull()
                            else -> null
                        } ?: throw MalformedXMLException("unknown entity '&$entity;'")
                        appendCodePoint(codePoint, entity)
                    }
                }
                i = end + 1
            }
        }
    }

    private fun StringBuilder.appendCodePoint(codePoint: Int, entity: String) {
        val isValidXmlCharacter =
            codePoint == 0x9 || codePoint == 0xa || codePoint == 0xd ||
                codePoint in 0x20..0xd7ff ||
                codePoint in 0xe000..0xfffd ||
                codePoint in 0x10000..0x10ffff
        if (!isValidXmlCharacter) {
            throw MalformedXMLException("invalid entity '&$entity;'")
        }
        if (codePoint <= 0xffff) {
            append(codePoint.toChar())
        } else {
            val supplementary = codePoint - 0x10000
            append(((supplementary ushr 10) + 0xd800).toChar())
            append(((supplementary and 0x3ff) + 0xdc00).toChar())
        }
    }
}

private fun String.isXmlNamePart(): Boolean {
    if (isEmpty()) return false
    if (!(first() == '_' || first().isLetter() || first().code >= 0x80)) return false
    return drop(1).all { character ->
        character == '_' || character == '-' || character == '.' ||
            character.isLetterOrDigit() || character.code >= 0x80
    }
}
