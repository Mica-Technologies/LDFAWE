#!/usr/bin/env bash
#
# Boot a dedicated server via `./gradlew runServer` and assert it reaches "Done (".
#
# Canonical copy from dev-configurations (minecraft-mod-workflows/template/). This version is
# for RFG / GregTechCEu-buildscript mods; the ForgeGradle 2.3 variant (which must also read
# run/logs/latest.log) is described in that folder's README.
#
# Why this exists: mod code that compiles fine can still be impossible to load on a
# dedicated server — a client-only class referenced from common code, a client mod
# declared as a hard dependency, a Side.CLIENT packet handler that touches Minecraft
# directly. Forge only catches those at server startup, so `./gradlew build` is
# perfectly happy right up until production dies. SUM shipped three such bugs at once
# in 2026.07.19 and took the Alto server down; every one of them was reachable from a
# single server boot.
#
# `runServer` never returns on success, so we background it, tail the log for a verdict,
# then shut it down.
#
# Env:
#   SMOKE_TIMEOUT   seconds to wait for startup (default 900)
#   SMOKE_LOG       log file path (default server-smoke.log)
#   GRADLE_ARGS     extra arguments for the runServer line (default none), e.g. CSM's
#                   `-PcsmRunModules=core` to boot with only its mandatory jar.

set -uo pipefail

TIMEOUT="${SMOKE_TIMEOUT:-900}"
LOG="${SMOKE_LOG:-server-smoke.log}"
GRADLE_ARGS="${GRADLE_ARGS:-}"

# Signals a successfully started dedicated server.
SUCCESS_RE='Done \([0-9.]+s\)!'

# Any of these mean the server is not coming up. "for invalid side" is the specific
# signature of client-only code reaching the server.
FAILURE_RE='Encountered an unexpected exception|MissingModsException|for invalid side|A fatal error has occurred|The state engine was in incorrect state|Failed to start the minecraft server|FML has found a problem'

# Accept the EULA in both possible run directories. RFG's runServer uses `run/` unless the
# buildscript property `separateRunDirectories` is true, in which case it's `run/server/`.
# It defaults to false, so `run/` is the one that actually matters today — but writing both
# keeps this working if that property is ever flipped. (Getting this wrong is invisible
# locally, where an already-accepted run/eula.txt lingers from earlier manual runs, and only
# shows up on a clean CI checkout as "Minecraft EULA not accepted".)
mkdir -p run run/server
printf 'eula=true\n' > run/eula.txt
printf 'eula=true\n' > run/server/eula.txt

echo "==> Starting dedicated server (timeout ${TIMEOUT}s)${GRADLE_ARGS:+ with ${GRADLE_ARGS}}"
# GRADLE_ARGS is deliberately unquoted: it may carry more than one argument.
# shellcheck disable=SC2086
./gradlew runServer $GRADLE_ARGS \
  -Dhttp.socketTimeout=60000 -Dhttp.connectionTimeout=60000 \
  -Dorg.gradle.internal.http.socketTimeout=60000 \
  -Dorg.gradle.internal.http.connectionTimeout=60000 \
  > "$LOG" 2>&1 &
GRADLE_PID=$!

verdict="timeout"
elapsed=0
while [ "$elapsed" -lt "$TIMEOUT" ]; do
  if grep -qE "$FAILURE_RE" "$LOG" 2>/dev/null; then
    verdict="crash"
    break
  fi
  if grep -qE "$SUCCESS_RE" "$LOG" 2>/dev/null; then
    verdict="ok"
    break
  fi
  if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
    # Gradle exited without ever printing "Done (" — build failure or early abort.
    verdict="exited"
    break
  fi
  sleep 5
  elapsed=$((elapsed + 5))
done

echo "==> Stopping server (verdict: ${verdict}, after ${elapsed}s)"
kill "$GRADLE_PID" 2>/dev/null
# The Gradle wrapper spawns the server in a child JVM; kill it too so the runner
# doesn't hang waiting on an orphan.
pkill -f 'net.minecraft.server' 2>/dev/null
pkill -f 'GradleWrapperMain' 2>/dev/null
wait "$GRADLE_PID" 2>/dev/null

if [ "$verdict" = "ok" ]; then
  echo "==> PASS: dedicated server reached startup"
  grep -E "$SUCCESS_RE" "$LOG" | head -1
  exit 0
fi

echo "==> FAIL: dedicated server did not start (${verdict})"
echo "----- matching failure lines -----"
grep -nE "$FAILURE_RE" "$LOG" | head -20
echo "----- last 120 log lines -----"
tail -120 "$LOG"
exit 1
