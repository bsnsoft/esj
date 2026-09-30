<?xml version="1.0" encoding="UTF-8"?>
<!--
  A Schematron schema written for the tests of esj-syntax: three rules of an example
  profile over a UBL invoice, one fatal, one a warning, one that calls a function the
  schema declares. Copyright 2026 BSNSoft Solutions GmbH. Licensed under the Apache
  License, Version 2.0.
-->
<schema xmlns="http://purl.oclc.org/dsdl/schematron" xmlns:u="urn:esj.example:functions"
        queryBinding="xslt2">
  <title>Rules of an example profile</title>
  <ns prefix="cbc" uri="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"/>
  <ns prefix="cac" uri="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"/>
  <ns prefix="ubl" uri="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"/>
  <ns prefix="xs" uri="http://www.w3.org/2001/XMLSchema"/>
  <ns prefix="u" uri="urn:esj.example:functions"/>
  <let name="currency" value="normalize-space(/ubl:Invoice/cbc:DocumentCurrencyCode)"/>
  <function xmlns="http://www.w3.org/1999/XSL/Transform" name="u:some" as="xs:boolean">
    <param name="count"/>
    <sequence select="$count &gt; 0"/>
  </function>
  <pattern id="example">
    <rule context="/ubl:Invoice">
      <assert id="EXAMPLE-01" flag="fatal" test="string-length(cbc:ID) &lt;= 20">[EXAMPLE-01]-An invoice number has at most 20 characters.</assert>
      <assert id="EXAMPLE-02" flag="warning" test="$currency = 'EUR'">[EXAMPLE-02]-An invoice of this profile should be in euro.</assert>
      <assert id="EXAMPLE-03" flag="fatal" test="u:some(count(cac:InvoiceLine))">[EXAMPLE-03]-An invoice has a line.</assert>
    </rule>
  </pattern>
</schema>
