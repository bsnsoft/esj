<?xml version="1.0" encoding="UTF-8"?>
<!--
  The HTML rendering of this project: the vendored KoSIT XRechnung visualization, with one
  of its templates overridden.

  This file imports xrechnung-html.xsl of the KoSIT xrechnung-visualization, which is
  vendored unmodified beside it under the Apache License 2.0, and overrides exactly one of
  its template rules. The vendored bytes are untouched; xsl:import precedence is what puts
  the rule below in front of the one it copies.

  The template of the vendored stylesheet that matches xr:ADDITIONAL_SUPPORTING_DOCUMENTS
  writes two values of the document somewhere other than into text: the external document
  location (BT-124) becomes the target of a link, and the reference (BT-122), the media type
  and the file name of an attachment become the arguments of a script call inside single
  quotes. An invoice is a document written by a stranger, so this rule writes a location
  whose scheme is not http, https or mailto as text rather than as a link, and escapes the
  three values for the string literals they stand in. Everything else is the vendored rule,
  copied so that the page keeps the layout its readers know.

  Derived from src/xsl/xrechnung-html.xsl of
  https://github.com/itplr-kosit/xrechnung-visualization, tag v2026-08-31,
  Copyright Koordinierungsstelle fuer IT-Standards (KoSIT), licensed under the
  Apache License, Version 2.0. See kosit/LICENSE and kosit/README.md; NOTICE records the
  attribution and this change.
-->
<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:xr="urn:ce.eu:en16931:2017:xoev-de:kosit:standard:xrechnung-1"
  xmlns:xrf="https://projekte.kosit.org/xrechnung/xrechnung-visualization/functions"
  xmlns:esj="urn:de.bsnsoft.esj:render:html"
  exclude-result-prefixes="#all">

  <xsl:import href="xrechnung-html.xsl" />

  <!--
    The schemes a rendering makes a link of. Everything else - javascript:, data:, vbscript:,
    a scheme nobody has heard of, and a reference that is not absolute - is shown as the text
    it is, so that reading an invoice never runs what the invoice brought with it.
  -->
  <xsl:function name="esj:followable" as="xs:boolean">
    <xsl:param name="location" as="xs:string?" />
    <xsl:sequence
      select="matches(normalize-space(string($location)), '^(https?|mailto):', 'i')" />
  </xsl:function>

  <!--
    A value on its way into a single-quoted JavaScript string literal. The apostrophe ends
    that literal and the backslash escapes the next character, so both are escaped, and the
    three characters that would end the line the literal stands on become spaces. The order
    matters: the backslash is doubled before the apostrophe is escaped with one.
  -->
  <xsl:function name="esj:quoted" as="xs:string">
    <xsl:param name="value" as="item()?" />
    <xsl:sequence
      select='replace(replace(replace(string($value), "[&#9;&#10;&#13;]", " "),
                              "\\", "\\\\"),
                      "&#39;", "\\&#39;")' />
  </xsl:function>

  <xsl:template match="xr:ADDITIONAL_SUPPORTING_DOCUMENTS">
    <div class="boxtabelle boxinhalt borderSpacing" role="list">
      <div class="boxzeile" role="listitem">
        <div class="boxdaten legende">
          <xsl:value-of select="xrf:_('xr:Supporting_document_reference')" />:
        </div>
        <div data-title="BT-122" class="BT-122 boxdaten wert">
          <xsl:value-of select="xr:Supporting_document_reference" />
        </div>
      </div>
      <div class="boxzeile" role="listitem">
        <div class="boxdaten legende">
          <xsl:value-of select="xrf:_('xr:Supporting_document_description')" />:
        </div>
        <div data-title="BT-123" class="BT-123 boxdaten wert">
          <xsl:value-of select="xr:Supporting_document_description" />
        </div>
      </div>
      <div class="boxzeile" role="listitem">
        <div class="boxdaten legende">
          <xsl:value-of select="xrf:_('xr:External_document_location')" />:
        </div>
        <div data-title="BT-124" class="BT-124 boxdaten wert">
          <xsl:variable name="location" as="xs:string"
            select="string(xr:External_document_location)" />
          <xsl:choose>
            <xsl:when test="esj:followable($location)">
              <a href="{$location}" target="_blank">
                <xsl:value-of select="$location" />
              </a>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="$location" />
            </xsl:otherwise>
          </xsl:choose>
        </div>
      </div>
        
      <div class="boxzeile" role="listitem">
        <div class="boxdaten legende">
          <xsl:value-of select="xrf:_('xr:Attached_document')" />:
        </div>
        <!-- HTML5 restrictions for id attribute: must contain at least 1 character, can't contain any space characters -->
        <!-- JS restrictions for param in getElementById(id), in this case $doc-ref-id: case-sensitive string unique within the document -->
          <xsl:variable name="doc-ref-id" as="xs:string" select="translate(normalize-space(xr:Supporting_document_reference), ' ', '-')"/>
        <div data-title="BT-125" class="BT-125 boxdaten wert">
        <xsl:choose>
            <xsl:when test="empty(xr:Attached_document/text())">
                <xsl:value-of select="xrf:_('no-data')" />
            </xsl:when>
            <xsl:otherwise>
                <a href="#{$doc-ref-id}" onClick="downloadData('{esj:quoted($doc-ref-id)}', '{esj:quoted(xr:Attached_document/@mime_code)}', '{esj:quoted(xr:Attached_document/@filename)}')">
                    <xsl:value-of select="xrf:_('_open')" />
                </a>    
            </xsl:otherwise>
        </xsl:choose>    
        </div>
          <div id="{$doc-ref-id}" style="display:none;">
          <xsl:value-of select="xr:Attached_document" />
        </div>

      </div>
        
        
      <div class="boxzeile" role="listitem">
        <div class="boxdaten legende">
          <xsl:value-of select="xrf:_('xr:Attached_document/@mime_code')" />:
        </div>
        <div data-title="BT-125" class="BT-125 boxdaten wert">
          <xsl:value-of select="xr:Attached_document/@mime_code" />
        </div>
      </div>
      <div class="boxzeile" role="listitem">
        <div class="boxdaten legende">
          <xsl:value-of select="xrf:_('xr:Attached_document/@filename')" />:
        </div>
        <div data-title="BT-125" class="BT-125 boxdaten wert">
          <xsl:value-of select="xr:Attached_document/@filename" />
        </div>
      </div>
    </div>
  </xsl:template>

</xsl:stylesheet>
