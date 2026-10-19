# Claude Code task: decode KEM AT-710 passive ADK stream

Read `docs/raw-logs/AT710-diagnostic-excerpts-2026-10-19.txt` and `docs/AT710-protocol-investigation-2026-10-19.md`, then inspect the existing Android diagnostic app and protocol/parser code.

## Goal
Determine which fields in the KEM AT-710 / TitrationViewer Android Accessory byte stream correspond to titration volume (mL), pH, and CO1 (mg), and implement a robust passive receive/parser path with tests. Do not fabricate protocol facts.

## Ground truth supplied by operator
- At 0 mL, pH = 9.363.
- CO1 = -0.3098 mg (approximately -0.3 mg).
- The operator clarified that 0.5, 1 and 2 are volume values in relevant observations; the third number is CO1.
- Manual rows in the notes may have their columns mislabeled. Never use those row labels as unquestioned ground truth.

## Required analysis
1. Inspect repository code and history first; report the current transport and parser architecture.
2. Search all code/docs/tests for `02 4C 61`, `02 58 36`, `02 58 37`, `0D 0A 0D 0A 04`, and the strings `ha`, `hb`, `CO1`.
3. Work from raw bytes. Explain each candidate field's byte offset, width, encoding, endianness, scale, and confidence. Distinguish confirmed facts from hypotheses.
4. Treat 29/32-byte USB reads as arbitrary chunks. Add buffering and record framing only where supported by observed delimiters; handle partial records and multiple records per read.
5. Do not send commands to the instrument. Keep communication passive/read-only unless a protocol command is independently documented and explicitly approved.
6. Add parser unit tests for split records, concatenated records, incomplete trailing records, and known sample bytes. Tests must not invent expected values for unknown fields.
7. If the available fragments are insufficient to identify fields, say exactly what additional capture is needed and add logging/export that preserves every byte (HEX plus raw bytes, timestamp, session boundaries and byte counts).
8. Keep volume, pH and CO1 as separate fields; do not silently swap labels. Use the zero-volume pH 9.363 and CO1 -0.3098 mg as calibration checks only if the corresponding record/time can be aligned reliably.

## Data integrity warning
The file of excerpts explicitly is not the complete 8,178-byte stream. Do not report a complete protocol decode from it. Prioritize enabling an exact raw capture export and documenting how to import it once supplied.

## Expected output
- A concise report of findings and confidence levels.
- Parser implementation and tests if the repository supports it.
- A clear list of unknowns and the exact next capture needed.
- No APK claim unless a build was actually run and verified.
