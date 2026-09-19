# BEL consensus module

This module contains the protocol-critical Java primitives for the finalized
BEL protocol at the pinned Besu 24.8.0 source tag:

- exact quorum and Byzantine-tolerance arithmetic;
- canonical seed derivation;
- deterministic hash-index leader selection;
- 1.32%/minimum-70 committee arithmetic and ordering;
- an explicit RFC 9381 ECVRF-P256-SHA256-SSWU `VrfProvider` boundary.

The `VrfProvider` interface intentionally has no hash-based fallback. The module
`DeterministicTestVrfProvider` is available only for local protocol
demonstrations: **test-only provider; not RFC 9381 cryptography; not
production-grade**. It must not determine a production committee. The module
must not be described as production-ready while the RFC backend is unresolved.
The bounded
investigation found that 0.0.5 does not build against the resolved hash2curve
API, while 0.0.6 and 0.0.7 fail RFC public-key derivation and proof-generation
interoperability. For the hackathon, `BelValidatorProvider` and
`BelProposerSelector` connect the deterministic test provider to Besu's existing
QBFT proposal, PREPARE, COMMIT, round-change, quorum, commit-seal, and block
import path. Besu's P2P, transaction execution, storage, and block import
infrastructure are reused unchanged.
