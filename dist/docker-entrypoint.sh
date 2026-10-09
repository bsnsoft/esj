#!/bin/sh
# The entry point of the container image: one document per process, a heap
# ceiling the process cannot talk its way past, and an abort on heap exhaustion
# (docs/deployment.md).
#
#   ESJ_MAX_HEAP     the heap ceiling of a command, and of every child process
#                    esj serve and esj mcp start (their --job-heap); 512m by default
#   ESJ_SERVE_HEAP   the heap ceiling of the server process of esj serve and esj mcp,
#                    which reads no document and holds no answer whole; 256m by default
#   ESJ_JAVA_OPTS    replaces every option this script would pass
#   ESJ_AOT          0 leaves the ahead-of-time cache out
set -eu
heap=${ESJ_MAX_HEAP:-512m}
case ${1-} in
  serve|mcp) heap=${ESJ_SERVE_HEAP:-256m} ;;
esac
# The cache and the object header layout are one setting: a cache recorded with
# compact headers is refused by a virtual machine running without them, so the
# two are passed together and after the caller's options.
aot=
if [ "${ESJ_AOT-1}" != 0 ] && [ -f /opt/esj/esj.aot ]; then
  aot="-XX:+UseCompactObjectHeaders -XX:AOTCache=/opt/esj/esj.aot"
fi
exec java \
  ${ESJ_JAVA_OPTS--Xms32m -Xmx$heap -XX:+ExitOnOutOfMemoryError} \
  $aot -jar /opt/esj/esj.jar "$@"
