> Catatan aktif: aturan lintas repo lama sudah dihapus. ToolBox dikerjakan di repo publik utama; repo private hanya untuk secret, signing, build final, Firebase/final runtime test, dan release sensitif.

## 5. Metode yang Tidak Menjadi Proof Mandiri

Tidak cukup sendiri:

- statement/line coverage tinggi;
- jumlah test besar;
- “semua unit test hijau”;
- statistical reliability / operational profile;
- exploratory testing;
- AI/LLM review/generation;
- bug-free history;
- zero crash telemetry;
- one device/emulator;
- one static analyzer;
- one fuzz campaign;
- one formal model yang tidak ditrace ke implementation;
- assurance case tanpa objective evidence.

Teknik tersebut boleh menjadi supporting evidence sesuai metode aktif.

---

## 6. Final Fault / Evidence Closure

R9 wajib menghasilkan:

```text
TOTAL_REQUIRED_CLAIMS = N
PROVEN_CLAIMS = N
UNPROVEN_CLAIMS = 0
TOTAL_FAULT_CLASSES = F
FAULT_CLASSES_CHALLENGED = F
FAULT_ESCAPE = 0
UNOWNED_FAULT_CLASS = 0
UNKNOWN = 0
SKIPPED = 0
INDETERMINATE = 0
STALE_EVIDENCE = 0
UNRESOLVED_DEFEATER = 0
UNDECLARED_MATERIAL_ASSUMPTION = 0
```

---

## 7. PASS Formula

`APP_SAFE_R9_PASS` hanya jika:

```text
APP_SAFE_R1_PASS
AND APP_SAFE_R2_PASS
AND APP_SAFE_R3_PASS
AND APP_SAFE_R4_PASS
AND APP_SAFE_R5_PASS
AND APP_SAFE_R6_PASS
AND APP_SAFE_R7_PASS
AND APP_SAFE_R8_PASS
AND REQUIRED_ASSET_PROOF_PASS_IF_APPLICABLE
AND CrossDomainInteractionClosure
AND ClaimEvidenceClosure
AND FaultModelClosure
AND MutationFaultChallengeClosure
AND ToolQualificationClosure
AND EvidenceFreshnessClosure
AND DefeaterClosure
AND UNKNOWN = 0
AND SKIPPED = 0
AND UNPROVEN = 0
AND FAULT_ESCAPE = 0
```

R9 PASS adalah prasyarat final `APPLICATION_SAFE_100`, bukan sinonim otomatis bila scope/platform boundary belum dikunci.

R9 PASS juga bukan alasan untuk menjalankan Firebase tambahan. Setiap Firebase final execution hanya di Private dan tetap memerlukan approval pengguna baru untuk setiap attempt.
