package org.jetbrains.compose.resources.vector.xmldom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PortableXmlParserTest {
    @Test
    fun parsesVectorDrawableNamespacesAndEntities() {
        val vector = parsePortableXml(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <!-- generated vector drawable -->
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="24dp" android:label="A &amp; B">
                    <path android:pathData="M0,0"/><![CDATA[🙂]]>
                </vector>
            """.trimIndent()
        )

        assertEquals("vector", vector.nodeName)
        assertEquals("24dp", vector.getAttributeNS(ANDROID_NAMESPACE, "width"))
        assertEquals("A & B", vector.getAttributeNS(ANDROID_NAMESPACE, "label"))
        assertEquals(1, vector.childNodes.length)
        assertEquals("path", vector.childNodes.item(0).nodeName)
        assertEquals("🙂", vector.textContent?.trim())
    }

    @Test
    fun restoresNamespaceMappingAfterNestedOverride() {
        val root = parsePortableXml(
            """
                <root xmlns:a="outer">
                    <inner xmlns:a="inner"><a:item/></inner>
                    <a:item/>
                </root>
            """.trimIndent()
        )

        val inner = root.childNodes.item(0)
        assertEquals("inner", inner.childNodes.item(0).namespaceURI)
        assertEquals("outer", root.childNodes.item(1).namespaceURI)
    }

    @Test
    fun resolvesAttributesByExpandedNamespaceName() {
        val root = parsePortableXml(
            """
                <root xmlns="urn:element" xmlns:a="urn:attribute" xmlns:b="urn:attribute"
                    plain="plain-value" a:value="namespaced-value"/>
            """.trimIndent()
        )

        assertEquals("urn:element", root.namespaceURI)
        assertEquals("plain-value", root.getAttributeNS("", "plain"))
        assertEquals("", root.getAttributeNS("urn:element", "plain"))
        assertEquals("namespaced-value", root.getAttributeNS("urn:attribute", "value"))
    }

    @Test
    fun decodesSupplementaryNumericEntity() {
        assertEquals("🙂", parsePortableXml("<root>&#x1F642;</root>").textContent)
        assertEquals("🙂", parsePortableXml("<root>&#128578;</root>").textContent)
    }

    @Test
    fun handlesPrologCommentsCDataAndPredefinedEntities() {
        val root = parsePortableXml(
            """
                <?xml version="1.0"?>
                <!-- before root -->
                <root>&lt;&gt;&amp;&quot;&apos;<![CDATA[<&raw>]]><!-- inside root --></root>
                <!-- after root -->
            """.trimIndent()
        )

        assertEquals("<>&\"'<&raw>", root.textContent)
    }

    @Test
    fun rejectsMalformedDocuments() {
        assertFailsWith<MalformedXMLException> { parsePortableXml("<first/><second/>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<root value='one' value='two'/>") }
        assertFailsWith<MalformedXMLException> {
            parsePortableXml("<root xmlns:a='same' xmlns:b='same' a:value='one' b:value='two'/>")
        }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<missing:root/>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<root></other>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<root>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("</root>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<!DOCTYPE root><root/>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<root>&unknown;</root>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<root>&#0;</root>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<?xml version='1.0'<root/>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<!-- unfinished <root/>") }
        assertFailsWith<MalformedXMLException> { parsePortableXml("<root><![CDATA[unfinished</root>") }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
