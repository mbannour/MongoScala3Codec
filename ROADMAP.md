# MongoScala3Codec — Roadmap

> **MongoScala3Codec — Compile-time safe MongoDB for Scala 3.**
> Native BSON codecs, type-safe field paths, filters and updates, built directly on the official MongoDB driver. No JSON intermediary. No effect-system lock-in.

---

## Survival Strategy

The official `mongo-scala-driver` now publishes a native Scala 3 build (since 5.7.0). The question every user asks is:

> "The official driver supports Scala 3 now. Why should I keep another dependency?"

The answer:

> "Because MongoScala3Codec gives you the official MongoDB codec model but makes your MongoDB code safer at compile time — and protects you from schema and refactoring mistakes."

A team can remove MongoScala3Codec. They simply don't want to, because they'd lose useful safety. That is the goal: **low adoption risk, high continuing value**.

---

## Architecture

```
         Application
              │
    MongoScala3Codec
              │
 ┌────────────┴─────────────────────┐
 │  Codec derivation                │
 │  MongoPath (field paths)         │
 │  typed filters / updates         │
 │  sort / projection / index       │
 │  CodecTestKit + codec laws       │
 │  schema evolution tests          │
 └─────────────┬────────────────────┘
               │
      Official MongoDB Driver
```

MongoScala3Codec adds compile-time safety. The official driver remains the API.

---

## Four Promises

### 1. COMPATIBILITY — Existing users keep working

- Binary and source compatibility policy (MiMa, CI-enforced)
- Existing BSON representations never change silently
- Existing annotations and derivation behavior preserved across minor versions
- Migration guide for every breaking change

**BSON Compatibility Contract (1.0 commitment):**
> Any BSON representation produced by a stable 1.x codec remains readable by later compatible 1.x releases unless explicitly documented as a breaking change.

This matters because BSON documents written today may live in production databases for 10+ years.

### 2. CORRECTNESS — A stable, documented type contract

1.0 does not mean "derivation can handle everything imaginable." It means the documented contract is stable and unsupported types fail clearly at compile time.

**Supported type contract for 1.0 (as measured, not as hoped):**

| Category | Types |
|---|---|
| Derived by us | the nine primitives, `UUID` (as a **String**), `ObjectId`, `Option[T]`, `List`/`Seq`/`Vector`/`Set`, `Map[String, V]`, case classes, sealed traits/classes, Scala 3 enums, self-recursive case classes through `Option` |
| Delegated to the registry | `Array[Byte]`, `java.math.BigDecimal`, `java.util.Date`, `Instant`, `LocalDate`, `LocalDateTime`, opaque type aliases, any user-provided `Codec[T]` |
| No codec — compiles, throws on encode | `scala.math.BigDecimal`, `scala.math.BigInt`, `ZonedDateTime`, `Array[T]` for `T` ≠ `Byte`, `Either[L, R]` |
| Rejected at compile time | non-`String` map keys, tuples, unstable (method-local) model paths, conflicting annotations, duplicate/empty discriminators |

Two corrections to earlier drafts of this table, both measured: `UUID` is stored as a **String**,
not Binary; and `Either` is **not** in the 1.0 contract. The full table, with the exact stored
documents, is in [README.md](README.md#supported-model-features) and
[docs/BSON_TYPE_MAPPING.md](docs/BSON_TYPE_MAPPING.md).

Where the shape can be known at compile time, it fails at compile time with a message naming the
model, the field and the fix. Where derivation has to defer to the registry — which is what lets
official and user codecs plug in — an unsupported type can only fail on first encode.

### 3. COMPILE-TIME SAFETY — The reason to stay

> The `MongoFilter` / `MongoIndex` snippets in this section are the **1.1+ direction**, not 1.0
> functionality. In 1.0 the compile-time safety is in derivation, the annotations and `MongoPath`.

After a developer renames a field, any stale reference in a filter, update, or projection becomes a compile error:

```scala
MongoFilter.gt[User](_.email, 18)
//                     ^^^^^
// MongoScala3Codec error: field `User.email` has type String.
// `gt` received a value of type Int.
// Expected: String  Found: Int
```

All methods return the official driver's `Bson` type — full interoperability, no new client:

```scala
// These are equivalent and both work:
collection.find(MongoFilter.eq[User](_.status, "active"))
collection.find(Filters.eq("status", "active"))
```

**MongoIndex** keeps type-safe field selection separate from MongoDB configuration:

```scala
// Right: field path is type-safe; options use the official driver API
val indexKeys = MongoIndex.ascending[User](_.email)
collection.createIndex(indexKeys, IndexOptions().unique(true))

// Not this: MongoScala3Codec does not wrap IndexOptions
```

This reinforces the fundamental rule: MongoScala3Codec adds compile-time safety; MongoDB's driver remains the API.

Progression:
- `MongoPath` (field paths) — already exists
- `MongoFilter` (filters)
- `MongoUpdate` (updates)
- `MongoSort` / `MongoProjection`
- `MongoIndex` (key specification only)

### 4. PRODUCTION SAFETY — CodecTestKit as a product

- Codec laws (encode → decode identity, encode stability)
- ScalaCheck-based property testing
- Schema-compatibility tests: documents written by model v1 must decode with model v2
- Unknown-field behavior (configurable)
- Migration fixture helpers

---

## Release Plan

### v1.0 — Trust

**Gate:** Do not tag 1.0 until every item below is complete. Do not start v1.1 until the BSON format, compatibility policy, annotation semantics, discriminator representation, recursive derivation, and compiler diagnostics are genuinely stable. These decisions may need to be supported for years.

- [x] Fix all FIXME / broken tests
- [x] Explicit supported-type contract, reflected in docs and in compile errors
- [x] Recursive (self-referential) type support
- [x] `@BsonIgnore` annotation (compile error if the field has no default)
- [x] `@BsonId` annotation
- [x] Discriminator design freeze + `@BsonDiscriminator("value")` per-subtype annotation
- [x] Compiler error quality pass — unsupported shapes name the type, the field and the fix
- [x] **BSON compatibility contract** — golden fixtures asserting the exact document for every
      supported feature, so a macro refactor cannot change the wire format quietly
- [x] Property-based tests over generated values
- [x] Schema-evolution fixtures (documents written by model v1, read by model v2)
- [x] MiMa binary compatibility policy, CI-enforced
- [x] Scala compiler compatibility matrix
- [x] MongoDB driver compatibility matrix
- [x] Documentation review
- [ ] ~~`Either[L, R]` encoding~~ — **dropped from 1.0.** `Either` stays outside the contract; a
      sealed trait expresses the same thing with an explicit discriminator
- [ ] `CodecTestKit` published as a separate artifact (`mongoscala3codec-testkit`) — deferred; the
      kit ships in the main artifact for 1.0
- [ ] Decide which `mongo-scala-bson` artifact 1.0 declares, now that a native `_3` build exists
      (see [README.md](README.md#if-you-use-the-native-scala-3-driver-mongo-scala-driver_3))
- [ ] Tag 1.0.0

---

### v1.1 — Compile-Time Safety: Filters + Updates

**Not available in 1.0.** Illustrative syntax, not a committed API:

```scala
MongoFilter.eq[User](_.age, 25)
MongoFilter.gt[User](_.age, 18)
MongoFilter.in[User](_.status, List("active", "trial"))
MongoFilter.exists[User](_.email)
MongoFilter.regex[User](_.name, "^Ali")

MongoUpdate.set[User](_.name, "Alice")
MongoUpdate.inc[User](_.loginCount, 1)      // compile error if field is not numeric
MongoUpdate.unset[User](_.resetToken)
MongoUpdate.push[User](_.tags, "scala")     // compile error if field is not a collection
```

All return `Bson` — fully interoperable with `Filters.*` and `Updates.*`.

---

### v1.2 — Production Safety: TestKit + Schema Evolution

- Codec laws (`CodecLaws[T]` ScalaCheck mixin)
- Unknown-field behavior (`UnknownFieldStrategy` in `CodecConfig`)
- Schema migration fixture helpers in `CodecTestKit`
- JMH benchmark suite with results published

---

### v1.3 — Compile-Time Safety: Sort + Projection + Index

**Not available in 1.0.** Illustrative syntax, not a committed API:

```scala
MongoProjection.include[User](_.name, _.address.city)
MongoProjection.exclude[User](_.password)

MongoSort.asc[User](_.createdAt)
MongoSort.desc[User](_.score)

// Index: type-safe key specification only; IndexOptions remain the driver's API
val keys = MongoIndex.ascending[User](_.email)
collection.createIndex(keys, IndexOptions().unique(true))

val compound = MongoIndex.compound[User](_.lastName, _.firstName)
```

---

### v2.0 — Only If Real Users Demand It

Cats Effect module, ZIO module, mongo4cats bridge, Giter8 template. Do not build for hypothetical users.

---

## The Developer Experience We Are Building Toward

> Everything below except the codec line is **illustrative future syntax**. `MongoFilter`,
> `MongoUpdate`, `MongoSort`, `MongoProjection` and `MongoIndex` **do not exist in 1.0**.

```scala
case class User(email: String, age: Int, tags: List[String], createdAt: Instant)

// Codec — zero boilerplate. This part exists today:
val registry = RegistryBuilder.from(MongoClient.DEFAULT_CODEC_REGISTRY).register[User].build

// Query — type-safe
MongoFilter.gt[User](_.age, 18)

// Update — type-safe
MongoUpdate.push[User](_.tags, "scala")

// Sort — type-safe
MongoSort.desc[User](_.createdAt)

// Projection — type-safe
MongoProjection.include[User](_.email, _.createdAt)

// This fails to compile with a useful error:
MongoFilter.gt[User](_.email, 18)
// MongoScala3Codec error:
// Field `User.email` has type String.
// `gt` received a value of type Int.
// Expected: String  Found: Int
```

The official driver can improve its codecs. MongoDB can improve its macros. Cats/ZIO libraries come and go. But once developers are accustomed to MongoDB mistakes becoming compiler errors, removing MongoScala3Codec becomes unattractive.

---

## What We Are Not Building On This Roadmap

| Not building | Why |
|---|---|
| Own MongoDB client | Maintenance cost; we benefit from official driver improvements for free |
| Cats Effect / ZIO wrappers | No evidence of demand over plain Future; increases maintenance |
| JSON serialization | That is the mongo4cats model; our differentiator is the opposite |
| Play / http4s integration | Orthogonal concern; not a retention driver |
| Oolong integration | Repository is archived; our typed filters cover the same ground |
