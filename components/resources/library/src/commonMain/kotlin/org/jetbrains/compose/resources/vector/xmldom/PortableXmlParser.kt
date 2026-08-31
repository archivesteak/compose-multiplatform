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

private class PortableXmlParser(source: String) {
    private val xml = source.replace("\r\n", "\n").replace('\r', '\n')
    private var pos = 0
    private var documentStart = 0
    private var root: PortableElementImpl? = null
    private val stack = ArrayDeque<PortableElementImpl>()
    private val prefixToUri = mutableMapOf(
        "xml" to "http://www.w3.org/XML/1998/namespace"
    )

    fun parseDocument(): Element {
        validateXmlCharacters(xml)
        if (xml.startsWith('\uFEFF')) pos++
        documentStart = pos
        while (pos < xml.length) {
            when {
                xml.startsWith("<?", pos) -> readProcessingInstruction()
                xml.startsWith("<!--", pos) -> readComment()
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
            val beforeWhitespace = pos
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
                    if (pos == beforeWhitespace) {
                        throw MalformedXMLException(
                            "expected whitespace before an attribute in <$nodeName>"
                        )
                    }
                    val attrName = readName()
                    skipWhitespace()
                    expect('=')
                    skipWhitespace()
                    val quote = xml.getOrNull(pos)
                    if (quote != '"' && quote != '\'') {
                        throw MalformedXMLException("expected a quoted value for '$attrName'")
                    }
                    pos++
                    val rawValue = readUntil(quote)
                    if ('<' in rawValue) {
                        throw MalformedXMLException("'<' is not allowed in attribute '$attrName'")
                    }
                    val normalizedRawValue = rawValue.map { character ->
                        if (character == '\t' || character == '\n') ' ' else character
                    }.joinToString("")
                    val value = decodeEntities(normalizedRawValue)
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
            validateNamespaceDeclaration(prefix, value)
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
        if (prefix == XMLNS_PREFIX) {
            throw MalformedXMLException("reserved prefix '$XMLNS_PREFIX' on element <$nodeName>")
        }
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
        val rawText = xml.substring(start, pos)
        if ("]]>" in rawText) {
            throw MalformedXMLException("']]>' is not allowed outside a CDATA section")
        }
        if (stack.isEmpty() && rawText.any { !it.isXmlWhitespace() }) {
            throw MalformedXMLException("text outside the root element")
        }
        appendText(decodeEntities(rawText))
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
        // DOM Element.textContent includes all descendant text in document order.
        stack.forEach { element ->
            element.textContent = element.textContent.orEmpty() + text
        }
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
            !xml[pos].isXmlWhitespace() &&
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

    private fun readProcessingInstruction() {
        val start = pos
        pos += 2
        val target = readName()
        if (!xml.startsWith("?>", pos) && xml.getOrNull(pos)?.isXmlWhitespace() != true) {
            throw MalformedXMLException("expected whitespace after processing-instruction target '$target'")
        }
        if (target.equals("xml", ignoreCase = true)) {
            if (target != "xml" || start != documentStart) {
                throw MalformedXMLException("the XML declaration must be lowercase and first in the document")
            }
        }
        val end = xml.indexOf("?>", pos)
        if (end < 0) throw MalformedXMLException("unterminated processing instruction at offset $start")
        pos = end + 2
    }

    private fun readComment() {
        val start = pos
        pos += "<!--".length
        val end = xml.indexOf("-->", pos)
        if (end < 0) throw MalformedXMLException("unterminated comment at offset $start")
        val content = xml.substring(pos, end)
        if ("--" in content || content.endsWith('-')) {
            throw MalformedXMLException("invalid '--' sequence in comment at offset $start")
        }
        pos = end + 3
    }

    private fun skipWhitespace() {
        while (pos < xml.length && xml[pos].isXmlWhitespace()) pos++
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
                            entity.startsWith("#x") &&
                                entity.substring(2).isNotEmpty() &&
                                entity.substring(2).all(Char::isHexDigit) ->
                                entity.substring(2).toIntOrNull(16)
                            entity.startsWith("#") &&
                                entity.substring(1).isNotEmpty() &&
                                entity.substring(1).all(Char::isDigit) ->
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

    private fun validateNamespaceDeclaration(prefix: String, uri: String) {
        when {
            prefix == XMLNS_PREFIX ->
                throw MalformedXMLException("the '$XMLNS_PREFIX' prefix cannot be declared")
            uri == XMLNS_NAMESPACE ->
                throw MalformedXMLException("the namespace '$XMLNS_NAMESPACE' cannot be bound")
            prefix == XML_PREFIX && uri != XML_NAMESPACE ->
                throw MalformedXMLException("the '$XML_PREFIX' prefix must use '$XML_NAMESPACE'")
            prefix != XML_PREFIX && uri == XML_NAMESPACE ->
                throw MalformedXMLException("only the '$XML_PREFIX' prefix may use '$XML_NAMESPACE'")
            prefix.isNotEmpty() && uri.isEmpty() ->
                throw MalformedXMLException("prefixed namespace '$prefix' must not be empty")
        }
    }
}

private fun validateXmlCharacters(value: String) {
    var index = 0
    while (index < value.length) {
        val first = value[index].code
        val codePoint = when {
            first in 0xd800..0xdbff -> {
                val second = value.getOrNull(index + 1)?.code
                    ?.takeIf { it in 0xdc00..0xdfff }
                    ?: throw MalformedXMLException("unpaired high surrogate at offset $index")
                index++
                0x10000 + ((first - 0xd800) shl 10) + (second - 0xdc00)
            }
            first in 0xdc00..0xdfff ->
                throw MalformedXMLException("unpaired low surrogate at offset $index")
            else -> first
        }
        val valid =
            codePoint == 0x9 || codePoint == 0xa || codePoint == 0xd ||
                codePoint in 0x20..0xd7ff ||
                codePoint in 0xe000..0xfffd ||
                codePoint in 0x10000..0x10ffff
        if (!valid) throw MalformedXMLException("invalid XML character at offset $index")
        index++
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun Char.isXmlWhitespace(): Boolean =
    this == ' ' || this == '\t' || this == '\n' || this == '\r'

private const val XML_PREFIX = "xml"
private const val XMLNS_PREFIX = "xmlns"
private const val XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace"
private const val XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/"

private fun String.isXmlNamePart(): Boolean {
    if (isEmpty()) return false
    var index = 0
    val first = xmlCodePointAt(index)
    if (!first.isXmlNameStartCharacter()) return false
    index += first.xmlCharacterWidth()
    while (index < length) {
        val codePoint = xmlCodePointAt(index)
        if (!codePoint.isXmlNameCharacter()) return false
        index += codePoint.xmlCharacterWidth()
    }
    return true
}

private fun String.xmlCodePointAt(index: Int): Int {
    val first = this[index].code
    if (first !in 0xd800..0xdbff) return first
    val second = this[index + 1].code
    return 0x10000 + ((first - 0xd800) shl 10) + (second - 0xdc00)
}

private fun Int.xmlCharacterWidth(): Int = if (this > 0xffff) 2 else 1

private fun Int.isXmlNameStartCharacter(): Boolean =
    this == '_'.code ||
        this in 'A'.code..'Z'.code ||
        this in 'a'.code..'z'.code ||
        this in 0xc0..0xd6 ||
        this in 0xd8..0xf6 ||
        this in 0xf8..0x2ff ||
        this in 0x370..0x37d ||
        this in 0x37f..0x1fff ||
        this in 0x200c..0x200d ||
        this in 0x2070..0x218f ||
        this in 0x2c00..0x2fef ||
        this in 0x3001..0xd7ff ||
        this in 0xf900..0xfdcf ||
        this in 0xfdf0..0xfffd ||
        this in 0x10000..0xeffff

private fun Int.isXmlNameCharacter(): Boolean =
    isXmlNameStartCharacter() ||
        this == '-'.code ||
        this == '.'.code ||
        this in '0'.code..'9'.code ||
        this == 0xb7 ||
        this in 0x300..0x36f ||
        this in 0x203f..0x2040
