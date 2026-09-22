@echo off
rem Runs the esj command line tool from a built tree; see bin/esj for the POSIX version.
rem The Java options are the ones docs\deployment.md prescribes; ESJ_JAVA_OPTS replaces them.
rem Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Buerckert. Licensed under the Apache License, Version 2.0.
setlocal
set "JAR=%~dp0..\esj-cli\target\esj.jar"
if not exist "%JAR%" (
  echo error: %JAR% does not exist; build it with "mvn -B verify" 1>&2
  rem 127 is outside the exit code table on purpose: nothing ran, so nothing was decided.
  exit /b 127
)
if not defined ESJ_JAVA_OPTS set "ESJ_JAVA_OPTS=-Xms32m -Xmx512m -XX:+ExitOnOutOfMemoryError"
java %ESJ_JAVA_OPTS% -jar "%JAR%" %*
