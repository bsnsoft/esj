<?xml version="1.0" encoding="UTF-8"?>
<!--
  A Schematron schema written for the tests of esj-syntax: it includes a second file,
  which a recipe never compiles. Copyright 2026 BSNSoft Solutions GmbH. Licensed under the
  Apache License, Version 2.0.
-->
<schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
  <title>A schema that includes another file</title>
  <include href="example-ubl.sch"/>
</schema>
