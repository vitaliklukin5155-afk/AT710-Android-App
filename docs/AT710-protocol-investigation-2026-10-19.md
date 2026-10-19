# KEM AT-710 — log capture notes (2026-10-19)

## Capture status
- Device reported by Android Accessory (ADK): Kyoto Electronics CO.,LTD.; model TitrationViewer; version 1.0.
- USB Host did not enumerate a device; Android Accessory channel was used.
- The diagnostic app was in passive receive mode; no commands were sent to the instrument during capture.
- Captured stream reported by the app: 8,178 bytes total.
- This file is an investigation note, **not a byte-for-byte replacement for the full diagnostic transcript**. The original transcript was pasted across multiple chat messages; the complete raw transcript is not currently available as one file for a lossless upload.

## Known framing / record patterns
- Initial control records observed: `02 58 36 0D 0A 0D 0A 04` and `02 58 37 0D 0A 0D 0A 04`.
- Repeating records commonly start with `02 4C 61` (ASCII prefix `.La`).
- Other records begin with `B...` and end-of-record sequences commonly include `0D 0A 0D 0A 04`.
- Reads arrive in chunks of 29 or 32 bytes and can split a logical record; parser should buffer and frame messages rather than treating each read as a complete record.
- A later text block includes `AT-710` and fields `ha`, `hb`, `i`, `j`, `k`, `l`, followed by empty `ma`–`me` and `na`–`ne` fields.

## Exact text values visible in the capture
```text
AT-710
ha 9.3629
hb 9.3559
i 0.00005
j 2.00005
k000614
l 9.88003
```

Selected raw record prefixes as pasted:
```text
.La 39999999999999ED3FB81E85EB51
B8224094D9224499224663DB81E85EB5
1B8224090000000000000000
.La 42000000000000F03FB81E85EB51
.La 47000000000000F23FB81E85EB51
.La 62000000000000F83F83C0CAA145
.La 72000000000000FC3FF5285C8FC2
```

## Control measurements supplied by the operator
- At 0 mL: pH = 9.363.
- CO1 = -0.3098 mg (approximately -0.3 mg).
- The operator clarified that 0.5, 1, and 2 are volume values in the relevant observations, and that the third number is CO1. Do not interpret the mislabeled manual lines as trustworthy field mappings without further confirmation.

Manual observations included in the diagnostic transcript (labels as pasted; operator warned that the labels may be confused):
- 2026-10-19 12:07:25 — `pH=0.5 | C01/CO1=9.360 | Объём mL=0.01`
- 2026-10-19 12:08:51 — `pH=1 | C01/CO1=9.360 | Объём mL=0.3275`
- 2026-10-19 12:11:48 — `pH=2 | C01/CO1=9.355 | Объём mL=0.9712`

## Next protocol-analysis steps
1. Preserve a complete raw capture as a separate file before transforming it.
2. Reassemble the byte stream using record terminators, accounting for chunks that contain multiple records or partial records.
3. Decode candidate numeric fields and test against the control measurement at 0 mL and later known volumes.
4. Do not assign semantic names to `ha`, `hb`, `i`, `j`, or `l` solely from a single capture.
