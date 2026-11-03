Issue #1060 — iOS press haptic investigation evidence

This directory is an inert archive, not application source or an active Xcode scheme.
The app remains at commit 3961d5cdc. No new Core Haptics production implementation
is included by archiving these files.

Results and limitations:
https://github.com/woowacourse-teams/2026-pheeeew/issues/1060#issuecomment-6596888545

Start with investigation-summary.json, results.json, native-results.json,
native-slow-results.json, candidate-results.json, and lifetime-result.json.
These reference the accompanying experiment logs. capture-stack.log records the
channel-allocation failure; lifetime-stack.log records the actual channel return.

experiment-source/ contains snapshots taken before removing temporary source,
Debug schemes, and LLDB helpers. Its nested manifest.json records original local
source hashes. archive-manifest.json records both original and archived hashes
and explicitly identifies redaction/path normalization. Archived line endings
are LF and trailing horizontal whitespace is removed. Files here are evidence;
do not copy the complete App.kt snapshot over a newer working tree.

Runner and capture scripts are historical orchestration, including failed attempts,
races, fixed PIDs, and version-specific breakpoint offsets. They are not maintained
portable tools. Device placeholders and PROJECT_ROOT/DERIVED_DATA must be configured
and the CoreHaptics UUID verified before adapting them to another environment.

Invalid timing trials and failed debugger/launch attempts are preserved and must
not be counted as successful trials. Error counts are observed log lines, not
physical vibration failure counts. Custom Core Haptics pulse feel was not verified
as equivalent to UIKit medium. Cleanup/build logs document build verification only.

Untouched originals remain locally under /private/tmp/haptic1060-comparison.
