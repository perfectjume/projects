# Clean Hit Indicator baseline

This branch builds the standalone `soulshade_hit_indicator` NeoForge 1.21.1 mod.

Provenance rule:
- no dependency on the legacy Hit Indicator JAR
- no `com.misanthropy` classes
- no inherited legacy config TOMLs
- no inherited indicator textures/assets
- procedural circle renderer
- clean NeoForge damage-delay/network/stasis implementation
- SoulShade-owned stasis grayscale and optional EMF pose preservation

The runtime workflow must pass before this baseline is distributed.
