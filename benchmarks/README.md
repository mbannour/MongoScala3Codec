# MongoScala3Codec Benchmarks (JMH)

This module contains JMH microbenchmarks for MongoScala3Codec. It focuses on round-trip (encode → decode) throughput and allocation for representative models.

## Scenarios
- Flat case class (primitives)
- Nested case class (with Option)
- ADT-heavy model (manual discriminator)
- Large collections (List/Vector/Map)

## Quick start

Prerequisites: JDK 17+, sbt, and enough heap for JMH (defaults are fine for smoke runs).

### Smoke run (fast)
Run a very short benchmark to verify everything compiles and runs:

```bash
sbt benchmarks/jmh:compile
sbt 'benchmarks/jmh:run -wi 0 -i 1 -f 1 -r 200ms .*'
```

- `-wi 0` — zero warmup iterations
- `-i 1` — 1 measurement iteration
- `-f 1` — 1 fork
- `-r 200ms` — 200ms per iteration

### Typical local run
Increase iterations/forks for more stable numbers:

```bash
sbt 'benchmarks/jmh:run -wi 3 -i 5 -f 2 -r 1s io.github.mbannour.bench.*'
```

### Selecting specific benchmarks
Use a regex to select:

```bash
sbt 'benchmarks/jmh:run -wi 3 -i 5 -f 2 -r 1s \
  .*CodecRoundTripBench.*roundTripNested.*'
```

## Adding new benchmarks
1. Create a new class under `benchmarks/src/main/scala/io/github/mbannour/bench/` with `@Benchmark` methods.
2. Prefer `@State(Scope.Benchmark)` and prepare test data in `@Setup(Level.Trial)`.
3. Use `Blackhole` to consume results to avoid dead-code elimination.
4. Keep smoke-friendly parameters in docs; do heavier runs locally.

## Notes
- These benchmarks avoid depending on `org.mongodb.scala.MongoClient`; instead they build a base BSON registry from primitive codecs.
- Benchmarks use the same codec derivation and round-trip utility as the main library (RegistryBuilder, CodecTestKit).

