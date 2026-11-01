# Capability reports

One JSON file per model and firmware version, written by `tools/firmware/extract.py` on a
maintainer's machine (see docs/DEVELOPMENT.md) and committed. CI checks them against the current
stubs without any firmware (`contract.py --check-reports`). A report holds facts only: which classes, methods and constants the app calls exist on that firmware, plus
the names of related methods worth investigating. It never contains firmware bytes.

A report proves an API **exists**, not that it **works**: a camera profile's `features` move from
`unknown` to `yes` only after a test on a real body (PROBE.TXT and a shooting test).
