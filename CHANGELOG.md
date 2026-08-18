# Change Log

## [Unreleased]
### Changed
- Rewritten for jolt. A duratom is now a durable atom over a pluggable
  `duratom.backends/StorageBackend` protocol, with file and sqlite/postgres
  (via jolt-lang/db) backends. It implements `glimmer.ratom/IReactiveCell`, so
  it composes with glimmer's `@`/`reset!`/`swap!`/`cursor`/`reaction`.
