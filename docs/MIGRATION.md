# Enterprise Migration Guide: MongoScala3Codec

Migrating from MongoDB Scala Driver Scala 2 codec ecosystem to MongoScala3Codec.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architectural Differences](#2-architectural-differences)
3. [Migration Strategies](#3-migration-strategies)
4. [Dependency Migration](#4-dependency-migration)
5. [Codec Registry Migration](#5-codec-registry-migration)
6. [Domain Model Migration](#6-domain-model-migration)
7. [BSON Compatibility Validation](#7-bson-compatibility-validation)
8. [Production Rollout Strategy](#8-production-rollout-strategy)
9. [Performance Migration Guide](#9-performance-migration-guide)
10. [Advanced Migration Topics](#10-advanced-migration-topics)
11. [Common Migration Problems](#11-common-migration-problems)
12. [Best Practices After Migration](#12-best-practices-after-migration)
13. [Migration Checklist](#13-migration-checklist)
14. [Real Production Example](#14-real-production-example)
15. [FAQ](#15-faq)

---

## 1. Executive Summary

### What is MongoScala3Codec?

MongoScala3Codec is a **compile-time BSON codec library** for Scala 3. It generates
type-safe MongoDB codecs entirely at compile time using Scala 3's inline macro system
and `Mirror`-based structural derivation. No reflection. No runtime type inspection.
No schema registration at startup.

The generated codecs are **drop-in compatible** with the existing MongoDB Java and
Scala drivers — they produce and consume exactly the same `CodecRegistry` interface
that `org.mongodb.scala` already knows how to use.

> **Note on driver 5.7+.** The official `mongo-scala-driver` only gained a Scala 3 codec
> macro in **5.7**; before that, `Macros.createCodecProvider` was Scala 2-only, which is the
> situation this guide was written for. If you are already on 5.7+ you have working codecs —
> MongoScala3Codec still adds the type-safe query/update/aggregation DSL, Scala 3 enum codecs,
> and configurable discriminators that the official macro does not provide. See the
> [comparison in the main README](../README.md#-why-mongoscala3codec).

### Why Migrate?

| Problem in the Scala 2 ecosystem | How MongoScala3Codec solves it |
|----------------------------------|-------------------------------|
| `Macros.createCodecProvider` was Scala 2-only before driver 5.7 | `RegistryBuilder.register[T]` — native Scala 3 since day one, plus a DSL the official 5.7 macro still lacks |
| Runtime reflection breaks GraalVM native image | Zero reflection — all logic generated at compile time |
| Manual codec implementations (~60-100 lines per type) | One line per type |
| `shapeless` / Magnolia derivation chains fail on Scala 3 | Purpose-built Scala 3 macro derivation |
| Discriminator fields on sealed traits require hand-rolled code | `registerSealed[T]` handles the full hierarchy automatically |
| KMongo's codec layer not maintained for Scala 3 | Direct BSON driver integration with no intermediary |
| Field name aliasing buried in annotations with mixed support | `@BsonProperty` respected uniformly at compile time |
| Type-unsafe string field paths in queries | `MongoPath.of[T](_.field.nested)` — compile-time verified |

### Migration Benefits

- **Zero-overhead codecs.** The generated code is equivalent to a hand-written codec;
  there is no wrapping, boxing, or virtual dispatch added.
- **Eliminated boilerplate.** A 25-type legacy codec layer of ~1,500 lines collapses
  to a single `registerAll` call.
- **Compile-time safety.** Unsupported types (regular classes, generic sealed traits)
  are rejected at compile time with actionable error messages, not at runtime during
  a production request.
- **GraalVM compatibility.** `native-image` builds work without codec reflection hints.
- **BSON wire compatibility.** Documents written by the old codec layer deserialize
  correctly by the new one, provided field names are preserved (see §7).

### Expected Effort

| Codebase scale | Estimated effort |
|----------------|-----------------|
| Small service, 5-15 domain types, no sealed traits | 2-4 hours |
| Medium service, 15-50 types, some sealed traits | 1-3 days |
| Large monolith / shared codec library, 50-200 types | 1-2 sprints |
| Platform with multiple services sharing codec registry | 1 sprint per service + 1 sprint for shared library |

The dominant cost is **BSON compatibility validation** (§7), not the mechanical code
change. Budget at least one day of golden-document testing per service regardless of
model count.

---

## 2. Architectural Differences

### Scala 2: Runtime Macro Expansion

The `Macros.createCodecProvider[T]()` call in the Scala 2 MongoDB driver expands a
macro at **compile time** to generate source that **calls into `scala.reflect`** at
runtime. The generated code uses `scala.reflect.runtime.universe` to introspect the
case class constructor, meaning:

- The `ClassLoader` must have access to the full `scala-reflect` JAR at runtime.
- Reflective access can be blocked by module-system restrictions (JDK 17+).
- GraalVM `native-image` requires reflection configuration metadata for every model.
- Error messages ("Cannot find codec for X") appear at request time, not at compile time.

```
┌──────────────────────────────────────────────────┐
│  Scala 2 Macro (compile-time source generation)  │
└──────────────────────┬───────────────────────────┘
                       │ generates
                       ▼
┌──────────────────────────────────────────────────┐
│  Reflective codec stub (runtime)                 │
│  Uses scala.reflect.runtime to read fields       │
└──────────────────────┬───────────────────────────┘
                       │ calls into
                       ▼
┌──────────────────────────────────────────────────┐
│  ClassInfo + FieldMirror per type                │
│  (latency on first access, JVM warm-up cost)     │
└──────────────────────────────────────────────────┘
```

### Scala 3: Compile-Time Inline Derivation

MongoScala3Codec uses `inline` macros and `scala.deriving.Mirror`. Every field access,
type dispatch, and BSON writer call is **inlined directly** into the call site at
compile time. The resulting bytecode contains no reflective calls and no codec registry
lookups in the hot path.

```
┌──────────────────────────────────────────────────┐
│  RegistryBuilder.register[User] (compile time)   │
│  Mirror.ProductOf[User] — field list known        │
└──────────────────────┬───────────────────────────┘
                       │ generates
                       ▼
┌──────────────────────────────────────────────────┐
│  Final Codec[User] class with:                   │
│  encode: writer.writeString("name", v.name)      │
│          writer.writeInt32("age", v.age)         │
│  decode: val name = reader.readString()          │
│          val age  = reader.readInt32()           │
│  — equivalent to hand-written codec              │
└──────────────────────────────────────────────────┘
```

### Side-by-Side Comparison

| Dimension | Scala 2 + `Macros.createCodecProvider` | MongoScala3Codec |
|-----------|----------------------------------------|------------------|
| Derivation mechanism | Macro → runtime `scala.reflect` | Inline macro → direct bytecode |
| Codec resolution | Runtime `CodecRegistry.get(clazz)` | Compile-time; provider registered once |
| Unsupported type detection | `CodecConfigurationException` at runtime | Compile error with guidance |
| JDK 17+ `--add-opens` | Sometimes required | Not required |
| GraalVM native-image | Needs reflect-config.json per model | Works out of the box |
| Option field handling | Driver default (encode as null) | Configurable via `NoneHandling` |
| Sealed trait polymorphism | Hand-rolled discriminator pattern | `registerSealed[T]` with `_type` field |
| Field path safety | String literals | `MongoPath.of[T](_.field)` |
| Startup cost | Reflective warm-up on first use | Zero — codec is pre-compiled |

### The RegistryBuilder

`RegistryBuilder` is a **fluent, immutable** registry composition API. It holds:

1. A **base registry** (usually `MongoClient.DEFAULT_CODEC_REGISTRY`) that handles all
   BSON primitives, `ObjectId`, `Instant`, `UUID`, etc.
2. A list of `CodecProvider` instances generated by the macro.
3. A `CodecConfig` that controls `NoneHandling`, discriminator field names, and
   discriminator strategy.

When `.build` is called, it calls `CodecRegistries.fromRegistries(providers, base)`.
The resulting `CodecRegistry` is a standard MongoDB `CodecRegistry` — it can be passed
anywhere the driver accepts one.

```scala
val registry: CodecRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)  // Step 1: set base
  .ignoreNone                                 // Step 2: configure
  .registerAll[(Address, User, Order)]        // Step 3: register
  .build                                      // Step 4: build
```

---

## 3. Migration Strategies

### 3.1 Big-Bang Migration

**Approach:** Replace all legacy codecs, providers, and registry construction in a
single PR.

**Suitable when:**
- Service has fewer than 20 domain types
- No shared codec library used by multiple services
- Comprehensive integration test coverage already exists
- Sealed trait patterns are absent or trivial

**Risks:**
- Large diff is hard to review
- Latent BSON compatibility issues surface all at once
- Rollback means reverting the entire PR

**Process:**

```
1. Create feature branch
2. Update build.sbt (§4)
3. Convert all domain models to Scala 3 case classes (§6)
4. Replace all registry construction sites
5. Run golden-document tests against production BSON snapshots (§7)
6. Deploy to staging, run full regression suite
7. Blue/green deploy to production
```

### 3.2 Incremental Type-by-Type Migration

**Approach:** Migrate one domain type at a time using a dual registry (§3.4). Each
type migrates independently with its own PR and validation.

**Suitable when:**
- Large codebase (50+ types)
- Limited test coverage
- Strict change-management processes
- Multiple teams owning different domain areas

**Risks:**
- Extended migration window (weeks to months)
- Dual-registry maintenance overhead
- Possible ordering constraints (types that contain other types must migrate together)

**Process:**

```
Week 1:  Add MongoScala3Codec dependency alongside old dependencies
Week 2+: Migrate leaf types first (types with no nested case class fields)
         Then migrate composite types (contain previously migrated types)
Week N:  Remove old codec layer entirely
```

### 3.3 Module-by-Module Migration

**Approach:** For multi-module sbt projects or microservice landscapes, migrate one
module/service at a time.

**Suitable when:**
- Each microservice owns its own domain models
- Services communicate via wire protocol (no shared Codec objects)
- Teams can release independently

**Key insight:** BSON documents in MongoDB do not carry codec metadata. If service A
writes a document with the old codec and service B reads it with MongoScala3Codec, the
only requirement is that **field names match**. The codec implementation is invisible
to MongoDB.

### 3.4 Dual Registry Strategy

The most production-safe incremental approach. Run both registries simultaneously and
route each type to its own registry.

```scala
import org.bson.codecs.configuration.CodecRegistries
import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.mongodb.scala.MongoClient

// Legacy registry — unchanged
val legacyRegistry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(
    Macros.createCodecProvider[LegacyOrder](),
    Macros.createCodecProvider[LegacyLineItem]()
  ),
  MongoClient.DEFAULT_CODEC_REGISTRY
)

// New registry — MongoScala3Codec for newly migrated types
val newRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .registerAll[(User, Address, Product)]
  .build

// Composite registry: new types win (first match wins in fromRegistries)
val compositeRegistry = CodecRegistries.fromRegistries(
  newRegistry,      // searched first
  legacyRegistry    // fallback
)

val client = MongoClient(
  MongoClientSettings.builder()
    .codecRegistry(compositeRegistry)
    .build()
)
```

**Important:** `CodecRegistries.fromRegistries` uses **first-match-wins** ordering.
Place the registry containing the type you want to WIN at the front.

### 3.5 Compatibility Layer Strategy

When a type has been migrated but old documents in MongoDB still exist in a format
produced by the legacy codec, wrap the new codec in a compatibility shim that handles
both the old and new BSON shapes.

```scala
// Old documents: {"fullName": "Alice Smith", "age": 30}
// New documents: {"name": "Alice Smith", "age": 30}   (field renamed)
//
// This codec reads both and always writes the new shape.

class CompatUserCodec(inner: Codec[User]) extends Codec[User]:
  override def encode(w: BsonWriter, v: User, ctx: EncoderContext): Unit =
    inner.encode(w, v, ctx)

  override def decode(r: BsonReader, ctx: DecoderContext): User =
    // Read raw document, handle both field names
    val doc = RawBsonDocumentCodec().decode(r, ctx)
    val name = Option(doc.get("name"))
      .orElse(Option(doc.get("fullName")))
      .map(_.asString.getValue)
      .getOrElse(throw new BsonInvalidOperationException("missing name/fullName"))
    val age = doc.getInt32("age").getValue
    User(name, age)

  override def getEncoderClass: Class[User] = classOf[User]
```

Use `withCodec(new CompatUserCodec(innerCodec))` when wiring the registry. Remove the
shim once all legacy documents have been migrated (background migration job or TTL).

---

## 4. Dependency Migration

### 4.1 Scala Version

MongoScala3Codec requires **Scala 3.3.1 or higher**.

```scala
// build.sbt — before
scalaVersion := "2.13.14"

// build.sbt — after
scalaVersion := "3.7.4"   // or any 3.3.1+
```

For cross-building (Scala 2 + Scala 3 during transition):

```scala
crossScalaVersions := Seq("2.13.14", "3.7.4")
```

MongoScala3Codec publishes under the `_3` suffix. As of driver **5.7**, `mongo-scala-bson`
and `mongo-scala-driver` publish native Scala 3 (`_3`) artifacts, so the old
`CrossVersion.for3Use2_13` shim is no longer needed.

### 4.2 sbt Dependencies

```scala
// Before — Scala 2 codec layer
libraryDependencies ++= Seq(
  "org.mongodb.scala"  %% "mongo-scala-driver"  % "4.x.x",
  "org.mongodb.scala"  %% "mongo-scala-bson"    % "4.x.x"
  // Possibly also:
  // "io.github.kirill5k" %% "mongo-scala-driver" % "..."    // KMongo-like wrappers
  // "org.reactivemongo"  %% "reactivemongo"       % "..."
)

// After — Scala 3 with MongoScala3Codec (native Scala 3 driver, no shim)
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec"    % "0.0.11",
  "org.mongodb.scala"  %% "mongo-scala-driver"  % "5.7.0",
  "org.mongodb.scala"  %% "mongo-scala-bson"    % "5.7.0"
)
```

**Version note:** Always use matching `mongo-scala-driver` and `mongo-scala-bson`
versions. The driver JAR version is displayed prominently in build.sbt in this project;
check the latest compatible pair in this library's own `build.sbt`.

### 4.3 Removing Legacy Codec Plugins

If you are using sbt plugins that generated Scala 2 codec boilerplate, remove them:

```scala
// project/plugins.sbt — remove
// addSbtPlugin("org.mongodb"  % "mongo-scala-bson-macros" % "...")
// addSbtPlugin("com.github.agourlay" % "cornichon" % "...")  // if used for BSON
```

### 4.4 Compatibility Matrix

| MongoScala3Codec | Scala | mongo-scala-driver | mongo-scala-bson | JDK |
|-----------------|-------|--------------------|------------------|-----|
| 0.0.11          | 3.3.1+ | 5.6.0             | 5.6.5            | 11+ |
| 0.0.10          | 3.3.1+ | 5.5.x             | 5.5.x            | 11+ |

---

## 5. Codec Registry Migration

### 5.1 From `Macros.createCodecProvider`

The most common Scala 2 pattern:

```scala
// BEFORE — Scala 2
import org.mongodb.scala.bson.codecs.Macros
import org.bson.codecs.configuration.CodecRegistries
import org.mongodb.scala.MongoClient

case class Address(street: String, city: String, zip: String)
case class User(_id: ObjectId, name: String, address: Address, age: Int)

val codecRegistry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(
    Macros.createCodecProvider[Address](),
    Macros.createCodecProvider[User]()
  ),
  MongoClient.DEFAULT_CODEC_REGISTRY
)
```

```scala
// AFTER — Scala 3 + MongoScala3Codec
import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.mongodb.scala.MongoClient

case class Address(street: String, city: String, zip: String)
case class User(_id: ObjectId, name: String, address: Address, age: Int)

val codecRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerAll[(Address, User)]
  .build
```

**Notes:**
- Registration order inside `registerAll` does not matter for nested types; the macro
  resolves the dependency graph at compile time.
- If types are registered with `register` instead of `registerAll`, register leaf types
  before composite types to avoid a runtime lookup miss on first use.

### 5.2 From Manual `CodecProvider` Implementation

```scala
// BEFORE — custom CodecProvider
class DomainCodecProvider extends CodecProvider:
  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    (clazz match
      case c if c == classOf[Address] => new AddressCodec()
      case c if c == classOf[User]    => new UserCodec(registry)
      case _                          => null
    ).asInstanceOf[Codec[T]]

val registry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(new DomainCodecProvider()),
  MongoClient.DEFAULT_CODEC_REGISTRY
)
```

```scala
// AFTER — RegistryBuilder replaces the provider completely
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerAll[(Address, User)]
  .build
```

### 5.3 Preserving Java Codecs

Many teams use Java codecs for types like `java.util.Date`, `java.time.Instant`,
custom UUID strategies, or Decimal128. These codecs continue to work because
`MongoClient.DEFAULT_CODEC_REGISTRY` (which is always the base) already handles these.
If you have **additional** Java codecs, carry them forward with `withProvider`:

```scala
// Custom Java codec provider preserved alongside new Scala 3 codecs
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(new MyJavaCodecProvider())   // unchanged Java codec
  .withProvider(new AnotherJavaProvider())   // unchanged Java codec
  .registerAll[(User, Order, Product)]        // new Scala 3 codecs
  .build
```

### 5.4 Mixed Registry (Transition Period)

During incremental migration, combine old and new codecs explicitly:

```scala
import org.bson.codecs.configuration.CodecRegistries

// Unmigrated types still use the old codec
val legacyProviders = CodecRegistries.fromProviders(
  Macros.createCodecProvider[LegacyReport](),
  Macros.createCodecProvider[LegacyConfig]()
)

// Migrated types use MongoScala3Codec
val modernRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerAll[(User, Order, Product, Address)]
  .build

// Compose: MongoScala3Codec wins on conflict (first match wins)
val transitionRegistry = CodecRegistries.fromRegistries(
  modernRegistry,
  legacyProviders,
  MongoClient.DEFAULT_CODEC_REGISTRY
)
```

### 5.5 From KMongo

KMongo's codec layer wraps `org.bson.codecs.pojo.PojoCodecProvider` (annotation-based
reflection) or uses its own `KMongoAnnotationProcessor`. Neither is available in
Scala 3. Replace the KMongo codec wiring entirely:

```scala
// BEFORE — KMongo
import org.litote.kmongo.KMongo
import org.litote.kmongo.reactivestreams.KMongo as KMongoReactive

val client = KMongo.createClient("mongodb://localhost:27017")
val database = client.getDatabase("mydb")
val col = database.getTypedCollection<User>()
```

```scala
// AFTER — standard MongoDB Scala driver + MongoScala3Codec
import org.mongodb.scala.{MongoClient, MongoClientSettings}
import io.github.mbannour.mongo.codecs.RegistryBuilder

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .registerAll[(User, Address, Order)]
  .build

val settings = MongoClientSettings.builder()
  .applyConnectionString(ConnectionString("mongodb://localhost:27017"))
  .codecRegistry(registry)
  .build()

val client   = MongoClient(settings)
val database = client.getDatabase("mydb")
val col      = database.getCollection[User]("users")
```

### 5.6 Enum Provider Registration

Scala 3 enums require an explicit provider. This is a common omission during migration.

```scala
import io.github.mbannour.mongo.codecs.{RegistryBuilder, EnumValueCodecProvider}

enum OrderStatus:
  case Pending, Confirmed, Shipped, Delivered, Cancelled

enum Priority:
  case Low, Medium, High, Critical

case class Order(
  _id:    ObjectId,
  status: OrderStatus,
  priority: Priority,
  items:  List[String]
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider.forStringEnum[OrderStatus])
  .withProvider(EnumValueCodecProvider.forStringEnum[Priority])
  .register[Order]
  .build
```

Use `forStringEnum` (stores enum by name, e.g. `"Shipped"`) for human-readable BSON
and schema stability. Use `forOrdinalEnum` (stores as integer index) only when storage
compactness is critical and enum member ordering is permanently frozen.

---

## 6. Domain Model Migration

### 6.1 Plain Case Classes

No changes required if the model is already a Scala 3 case class with supported field
types.

```scala
// BEFORE (Scala 2)
case class Product(
  _id:   ObjectId,
  name:  String,
  price: Double,
  sku:   String
)

// AFTER (Scala 3) — identical, no change
case class Product(
  _id:   ObjectId,
  name:  String,
  price: Double,
  sku:   String
)
```

### 6.2 Nested Documents

Nested case classes work automatically. All involved types must be registered.

```scala
case class GeoPoint(lat: Double, lon: Double)
case class Warehouse(location: GeoPoint, capacity: Int, code: String)
case class Shipment(_id: ObjectId, origin: Warehouse, destination: Warehouse)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerAll[(GeoPoint, Warehouse, Shipment)]
  .build
```

BSON output:
```json
{
  "_id": {"$oid": "..."},
  "origin": {
    "location": {"lat": 40.7128, "lon": -74.0060},
    "capacity": 500,
    "code": "NYC-01"
  },
  "destination": { ... }
}
```

### 6.3 Optional Fields

The default `NoneHandling` encodes `None` as BSON `null`. This matches the Scala 2
`Macros.createCodecProvider` default behaviour. If your existing documents store `null`
for absent optional fields, keep the default or explicitly set `NoneHandling.Encode`
to preserve BSON compatibility.

```scala
case class Customer(_id: ObjectId, name: String, phone: Option[String], vip: Option[Boolean])

// Preserve exact Scala 2 behaviour (None → null)
val registryEncodeNull = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Customer]
  .build
// Stored: {"_id": ..., "name": "Alice", "phone": null, "vip": null}

// Omit absent fields (cleaner documents, breaks queries that filter `{phone: null}`)
val registryIgnoreNone = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .register[Customer]
  .build
// Stored: {"_id": ..., "name": "Alice"}
```

**BSON compatibility warning:** Switching from `NoneHandling.Encode` to
`NoneHandling.Ignore` on an existing collection changes the document shape. Existing
queries like `db.customers.find({phone: null})` will no longer match documents that
omit the field. Audit your query patterns before changing `NoneHandling`.

### 6.4 Collections

`List[T]`, `Seq[T]`, `Vector[T]`, `Set[T]`, and `Map[String, T]` are all supported.
Register the element type.

```scala
case class Tag(name: String, weight: Double)
case class Article(
  _id:      ObjectId,
  title:    String,
  tags:     List[Tag],
  metadata: Map[String, String],
  revisions: Vector[Int]
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerAll[(Tag, Article)]
  .build
```

BSON output:
```json
{
  "title": "Hello World",
  "tags": [{"name": "scala", "weight": 0.9}, {"name": "mongodb", "weight": 0.7}],
  "metadata": {"author": "alice", "lang": "en"},
  "revisions": [1, 2, 3]
}
```

### 6.5 Sealed Traits (Polymorphism)

**Before — Scala 2 manual discriminator pattern:**

```scala
// Scala 2: manual discriminator codec — ~80 lines omitted for brevity
sealed trait Event
case class UserCreated(userId: String, ts: Long) extends Event
case class OrderPlaced(orderId: String, total: Double, ts: Long) extends Event
case class PaymentFailed(orderId: String, reason: String, ts: Long) extends Event

// Required hand-written codec that reads "_type" field and dispatches
class EventCodecProvider extends CodecProvider { /* ... */ }
```

**After — MongoScala3Codec:**

```scala
sealed trait Event
case class UserCreated(userId: String, ts: Long)                       extends Event
case class OrderPlaced(orderId: String, total: Double, ts: Long)       extends Event
case class PaymentFailed(orderId: String, reason: String, ts: Long)    extends Event

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Event]   // Registers Event + all three subtypes
  .build
```

BSON produced:
```json
{"_type": "UserCreated",   "userId": "u-123", "ts": 1700000000}
{"_type": "OrderPlaced",   "orderId": "o-456", "total": 99.0, "ts": 1700000001}
{"_type": "PaymentFailed", "orderId": "o-456", "reason": "NSF", "ts": 1700000002}
```

**Changing discriminator field name:**

If your legacy documents use a different discriminator key (e.g. `"type"`, `"kind"`,
`"__type"`), configure `CodecConfig` accordingly:

```scala
val cfg = CodecConfig(discriminatorField = "type")  // match legacy key

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(cfg)
  .registerSealed[Event]
  .build
```

**Changing discriminator values:**

By default the discriminator value is the simple class name (`"UserCreated"`). If your
legacy codec used fully qualified names or custom strings, use `DiscriminatorStrategy`:

```scala
import io.github.mbannour.mongo.codecs.DiscriminatorStrategy

// Legacy stored: "com.acme.events.UserCreated"
val cfg = CodecConfig(
  discriminatorField    = "_type",
  discriminatorStrategy = DiscriminatorStrategy.FullyQualifiedName
)

// Legacy stored custom strings: "user_created", "order_placed", etc.
val cfg2 = CodecConfig(
  discriminatorField    = "_type",
  discriminatorStrategy = DiscriminatorStrategy.Custom(Map(
    classOf[UserCreated]   -> "user_created",
    classOf[OrderPlaced]   -> "order_placed",
    classOf[PaymentFailed] -> "payment_failed"
  ))
)
```

### 6.6 Scala 3 Enums

```scala
// BEFORE — Scala 2 workaround (sealed trait as enum)
sealed trait Status
case object Active   extends Status
case object Inactive extends Status
case object Pending  extends Status

// Stored with manual codec as: {"status": "Active"} or {"status": 0}

// AFTER — Scala 3 enum
enum Status:
  case Active, Inactive, Pending

case class Account(_id: ObjectId, name: String, status: Status)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider.forStringEnum[Status])
  .register[Account]
  .build
// Stored: {"_id": ..., "name": "Acme", "status": "Active"}
```

**Migrating from ordinal-based storage:** If the Scala 2 codec stored enum values as
integers (common with Magnolia-based derivation or custom codecs), keep that strategy
to preserve BSON compatibility:

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider.forOrdinalEnum[Status])
  .register[Account]
  .build
// Stored: {"status": 0}  — matches legacy documents
```

### 6.7 Value Classes and Opaque Types

**Scala 2 value classes** do not carry their wrapped type through to BSON — the
underlying type is stored. Scala 3 opaque types preserve the same semantic.

```scala
// BEFORE — Scala 2 value class
class UserId(val value: String) extends AnyVal
case class User(_id: UserId, name: String)
// Stored: {"_id": "u-123", "name": "Alice"}

// AFTER — Scala 3 opaque type (same BSON wire format)
object Domain:
  opaque type UserId = String
  object UserId:
    def apply(v: String): UserId = v
    extension (u: UserId) def value: String = u

import Domain.*
case class User(_id: UserId, name: String)
// Stored: {"_id": "u-123", "name": "Alice"}  — identical BSON
```

### 6.8 Field Name Aliases

When a legacy codec stored fields under a different name than the Scala field name,
preserve the mapping with `@BsonProperty`:

```scala
// Legacy BSON: {"fullName": "Alice", "e": "alice@example.com", "dob": 631152000}

import org.mongodb.scala.bson.annotations.BsonProperty

case class User(
  @BsonProperty("fullName") name:      String,
  @BsonProperty("e")        email:     String,
  @BsonProperty("dob")      birthDate: Long
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build
// Reads and writes "fullName", "e", "dob" — matches legacy documents exactly
```

### 6.9 Recursive Structures

MongoScala3Codec supports recursive case classes with a depth limit enforced by the
BSON writer (not the macro):

```scala
case class TreeNode(
  value:    Int,
  children: List[TreeNode]
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[TreeNode]
  .build
```

**Known limitation:** The codec must be registered before the recursive reference is
resolved at runtime. `register[TreeNode]` handles self-reference correctly.

### 6.10 Types with Default Parameters

Missing BSON fields will be filled with Scala default parameter values during
decoding, exactly as with the Scala 2 macro:

```scala
case class Config(
  timeout:    Int    = 5000,
  retries:    Int    = 3,
  region:     String = "us-east-1",
  featureFlag: Boolean = false
)

// A document {"timeout": 9000} will decode to Config(9000, 3, "us-east-1", false)
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Config]
  .build
```

---

## 7. BSON Compatibility Validation

This is the most critical phase of any production migration. A codec change that alters
field names, field ordering, type encoding, or discriminator values can silently
corrupt existing data reads.

### 7.1 What Can Break BSON Compatibility

| Change | Risk |
|--------|------|
| Scala field name changed without `@BsonProperty` | Different key in BSON — old documents decode with field absent |
| `NoneHandling` changed (Encode → Ignore) | Absent key vs `null` — queries on `{field: null}` stop matching |
| Discriminator field name changed | Sealed trait decoder cannot find the discriminator |
| Discriminator value changed (SimpleName vs FQN) | Cannot dispatch to correct subtype |
| Enum encoding changed (string → ordinal) | Old `"Active"` fails to match ordinal `0` |
| Nested type registration order changed | No effect on BSON — registry-only concern |
| `@BsonProperty` added/removed | Field name changes — old documents unreadable |

### 7.2 Golden-Document Test Strategy

Capture BSON documents produced by the **legacy codec** before migration. After
migration, verify that the **new codec** reads those documents without data loss and
produces the same BSON on a round-trip.

#### Step 1: Capture golden documents

Run this against your production database **before** deploying any codec changes:

```scala
import org.mongodb.scala.*
import org.bson.RawBsonDocument
import org.bson.json.JsonWriterSettings
import java.nio.file.{Files, Paths}

// Capture raw BSON bytes — codec-independent
object GoldenCapture extends App:
  val client   = MongoClient("mongodb://localhost:27017")
  val db       = client.getDatabase("production")
  val col      = db.getCollection[RawBsonDocument]("users")

  val settings = JsonWriterSettings.builder().indent(true).build()

  val docs = col.find()
    .limit(100)
    .toFuture()
    .map: rawDocs =>
      rawDocs.zipWithIndex.foreach: (doc, i) =>
        Files.writeString(
          Paths.get(s"golden/users_$i.json"),
          doc.toJson(settings)
        )

  Await.result(docs, 30.seconds)
  client.close()
```

#### Step 2: Write a golden-document regression test

```scala
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecTestKit}
import org.bson.{BsonDocument, RawBsonDocument}
import org.bson.json.JsonMode
import org.mongodb.scala.MongoClient
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Paths}

class UserGoldenDocumentSpec extends AnyFlatSpec with Matchers:

  val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .ignoreNone
    .register[User]
    .build

  given codec: org.bson.codecs.Codec[User] = registry.get(classOf[User])

  "User codec" should "decode all golden documents without data loss" in:
    val goldenDir = Paths.get("src/test/resources/golden/users")
    Files.list(goldenDir).forEach: path =>
      val json     = Files.readString(path)
      val expected = BsonDocument.parse(json)

      // Decode using new codec
      val user     = CodecTestKit.fromBsonDocument[User](expected)

      // Re-encode using new codec
      val actual   = CodecTestKit.toBsonDocument(user)

      // Deep compare: every field in expected must appear with same value in actual
      withClue(s"Golden document $path:"):
        CodecTestKit.bsonDeepContains(actual, fieldsOf(expected)) shouldBe true

  private def fieldsOf(doc: BsonDocument): Map[String, org.bson.BsonValue] =
    import scala.jdk.CollectionConverters.*
    doc.entrySet().asScala.map(e => e.getKey -> e.getValue).toMap
```

#### Step 3: Validate serialization symmetry for all model variants

```scala
import org.scalacheck.{Gen, Arbitrary}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class UserCodecSymmetrySpec extends AnyFlatSpec
    with Matchers
    with ScalaCheckPropertyChecks:

  val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .ignoreNone
    .register[User]
    .build

  given codec: org.bson.codecs.Codec[User] = registry.get(classOf[User])

  given Arbitrary[User] = Arbitrary:
    for
      id    <- Gen.const(new ObjectId())
      name  <- Gen.alphaStr.suchThat(_.nonEmpty)
      email <- Gen.option(Gen.alphaStr.map(_ + "@example.com"))
      age   <- Gen.chooseNum(18, 120)
    yield User(id, name, email, age)

  "User codec" should "satisfy round-trip symmetry for all generated instances" in:
    forAll: (u: User) =>
      CodecTestKit.codecSymmetryProperty(u) shouldBe true
```

### 7.3 Field-by-Field Diff on Existing Collection

For collections with complex schemas, run a diff pass using both the old and new codecs
**in the same process** before cutting over:

```scala
object SchemaCompatibilityCheck extends App:
  val oldRegistry = /* legacy registry */ ???
  val newRegistry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .registerAll[(Address, User)]
    .build

  val client    = MongoClient("mongodb://localhost:27017")
  val rawCol    = client.getDatabase("prod").getCollection[RawBsonDocument]("users")

  val results = rawCol.find()
    .limit(10_000)
    .toFuture()
    .map: docs =>
      docs.map: rawDoc =>
        val expectedJson = rawDoc.toJson()

        // Decode + re-encode with old codec
        val oldUser  = rawDoc.decode(oldRegistry.get(classOf[User]))
        val oldBson  = encode(oldUser, oldRegistry)

        // Decode + re-encode with new codec
        val newUser  = rawDoc.decode(newRegistry.get(classOf[User]))
        val newBson  = encode(newUser, newRegistry)

        // Compare
        val diff = CodecTestKit.deepDiff(oldBson, newBson)
        if diff.nonEmpty then
          println(s"DIFF on ${rawDoc.get("_id")}: ${diff.mkString(", ")}")

  Await.result(results, 5.minutes)
```

### 7.4 None Handling Audit

Run this before changing `NoneHandling` to understand impact:

```scala
// Find documents where any of the optional fields are present as null
// If count > 0, switching to NoneHandling.Ignore changes behaviour for
// queries that filter {field: null}

db.users.countDocuments({
  "$or": [
    {"phone": null},
    {"vipLevel": null},
    {"referrer": null}
  ]
})
```

If the count is non-zero and queries use `{field: null}` filters, either:
1. Keep `NoneHandling.Encode` to preserve exact semantics, or
2. Migrate documents to remove null fields (background job), update queries to
   use `{field: {$exists: false}}` instead of `{field: null}`.

---

## 8. Production Rollout Strategy

### 8.1 Pre-Deployment Gate

Never deploy a codec migration without passing all three gates:

1. **Golden document tests** pass (§7.2) — new codec reads all sampled production docs
2. **Round-trip symmetry** passes for all model variants (§7.3)
3. **Schema diff** shows zero unexpected field changes (§7.3)

### 8.2 Canary Deployment

Route a small fraction of traffic through the new codec before full rollout.

```
         ┌─────────────────────┐
         │   Load Balancer     │
         └──────────┬──────────┘
                    │
        ┌───────────┴────────────┐
        │ 5% canary              │ 95% baseline
        ▼                        ▼
┌──────────────┐        ┌──────────────────┐
│ New service  │        │ Old service       │
│ (new codec)  │        │ (old codec)       │
└──────┬───────┘        └────────┬──────────┘
       │                         │
       └────────────┬────────────┘
                    ▼
             MongoDB Atlas
           (shared collection)
```

Both the old codec (writing legacy BSON) and the new codec (writing new BSON) must
be able to read the same documents for the duration of the canary window. This is
the main reason to validate golden documents thoroughly.

**Monitoring during canary:**
- Error rate on MongoDB operations: `db.runCommand({serverStatus: 1}).opcounters`
- `BsonInvalidOperationException` in application logs
- Codec-related metrics if you have custom instrumentation
- p99 latency on read/write operations (should not change)

### 8.3 Dual-Write Strategy

For critical collections, consider a dual-write window where both old and new codecs
write documents simultaneously. This provides a zero-risk rollback: simply stop
routing traffic to the new service instance.

```
Timeline:
  T0  Deploy new service (canary 5%)
  T+7 Validate golden docs, zero errors
  T+7 Increase canary to 25%
  T+14 Full rollout (100% new codec)
  T+21 Remove old codec from codebase
  T+28 Remove compatibility shims
```

### 8.4 Rollback Plan

| Scenario | Rollback action |
|----------|----------------|
| New codec writes documents that old codec cannot read | Rollback new service immediately; run a repair job to re-write affected documents using old codec |
| Field names changed; old queries fail | Add `@BsonProperty` to restore old field names; redeploy |
| Discriminator format changed | Restore `discriminatorField`/`discriminatorStrategy` config; redeploy |
| Enum encoding changed | Restore `forOrdinalEnum` vs `forStringEnum`; redeploy |

### 8.5 Observability Recommendations

Add codec-level instrumentation using MongoDB's `CommandListener`:

```scala
import com.mongodb.event.*
import io.micrometer.core.instrument.MeterRegistry

class CodecMetricsCommandListener(metrics: MeterRegistry) extends CommandListener:
  override def commandFailed(event: CommandFailedEvent): Unit =
    val cause = event.getThrowable.getMessage
    if cause.contains("codec") || cause.contains("BsonInvalidOperation") then
      metrics.counter(
        "mongodb.codec.error",
        "command", event.getCommandName,
        "database", event.getDatabaseName
      ).increment()

val settings = MongoClientSettings.builder()
  .addCommandListener(new CodecMetricsCommandListener(meterRegistry))
  .build()
```

---

## 9. Performance Migration Guide

### 9.1 Startup Time

The Scala 2 macro codec creates a provider that is evaluated lazily on first access.
This introduces a small JVM warm-up cost per codec type on the first request that
touches each collection. Under sustained load this is negligible, but it can cause
tail latency spikes at startup.

MongoScala3Codec generates the full codec bytecode at **compile time**. At JVM startup
the codec class is already in memory. There is no warm-up latency.

### 9.2 Serialization Throughput

Based on JMH benchmarks in this project's `benchmarks/` module:

| Type | Operation | ops/sec (approx.) |
|------|-----------|-------------------|
| Flat case class (5 fields) | Encode | ~500,000 |
| Flat case class (5 fields) | Decode | ~400,000 |
| Nested case class (3 levels) | Encode | ~200,000 |
| Sealed trait (5 subtypes) | Decode dispatch | ~350,000 |

These figures are comparable to hand-written codecs. The compile-time inlining
eliminates virtual dispatch overhead present in reflection-based codecs.

### 9.3 Allocation Patterns

The Scala 2 reflection-based codec allocates `FieldMirror` and `InstanceMirror`
objects per encode/decode invocation. MongoScala3Codec allocates only the result
case class instance — allocation behaviour is identical to a hand-written codec.

Verify with async-profiler if you have memory pressure concerns:

```bash
# Attach async-profiler, run your benchmark
java -agentpath:/opt/async-profiler/lib/libasyncProfiler.so=start,event=alloc,\
  file=alloc.html -jar myapp.jar
```

Look for `CodecRegistries$`, `ReflectiveCodec$`, `FieldMirror$` allocations disappearing
after migration.

### 9.4 Compile-Time Cost

The macro expansion adds ~10-30ms per registered type to incremental compile time.
For a 30-type domain model, expect 300-900ms of additional compile time. This is a
one-time cost per change to the model — unchanged types are cached.

Use `registerAll[(A, B, C, D)]` instead of sequential `register` calls. Batch
registration is substantially faster because the macro expands one tuple-of-types
rather than N independent macros.

```scala
// Slower — N separate macro expansions
val r = RegistryBuilder.from(base)
  .register[A].register[B].register[C].register[D].register[E].build

// Faster — single macro expansion over the tuple
val r = RegistryBuilder.from(base)
  .registerAll[(A, B, C, D, E)].build
```

Observed compile-time improvement: ~60% faster for 5+ type batches.

### 9.5 GraalVM Native Image

MongoScala3Codec is fully compatible with GraalVM `native-image` out of the box.
Remove any reflection hints you added for the Scala 2 codec layer:

```json
// BEFORE — reflect-config.json (no longer needed after migration)
[
  {"name": "com.acme.User", "allDeclaredFields": true, "allPublicMethods": true},
  {"name": "com.acme.Address", "allDeclaredFields": true}
]
```

No `reflect-config.json` entries are needed for types registered with
`RegistryBuilder`.

---

## 10. Advanced Migration Topics

### 10.1 ZIO Integration

```scala
import zio.*
import zio.stream.*
import org.mongodb.scala.*
import io.github.mbannour.mongo.codecs.RegistryBuilder

// Registry as a ZIO layer
val registryLayer: ULayer[CodecRegistry] =
  ZLayer.succeed:
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .registerAll[(User, Order, Product)]
      .build

// MongoDB client layer that depends on the registry
val mongoLayer: ZLayer[CodecRegistry, Throwable, MongoClient] =
  ZLayer.scoped:
    for
      registry <- ZIO.service[CodecRegistry]
      settings  = MongoClientSettings.builder()
                    .applyConnectionString(ConnectionString("mongodb://localhost:27017"))
                    .codecRegistry(registry)
                    .build()
      client   <- ZIO.acquireRelease(ZIO.attempt(MongoClient(settings)))(c =>
                    ZIO.attempt(c.close()).ignoreLogged
                  )
    yield client

// Usage in a ZIO app
val userRepo: ZLayer[MongoClient, Nothing, UserRepository] = ???
```

### 10.2 Cats Effect Integration

```scala
import cats.effect.*
import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.mongodb.scala.*

object MongoModule:
  def registry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .registerAll[(User, Order, Address)]
      .build

  def clientResource(uri: String): Resource[IO, MongoClient] =
    Resource.make(
      IO.delay:
        MongoClient(
          MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(uri))
            .codecRegistry(registry)
            .build()
        )
    )(c => IO.delay(c.close()))

  def collectionResource[T: ClassTag](
    client: MongoClient, db: String, coll: String
  ): Resource[IO, MongoCollection[T]] =
    Resource.pure:
      client.getDatabase(db).getCollection[T](coll).withCodecRegistry(registry)
```

### 10.3 Akka / Pekko Streams Integration

```scala
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.stream.Materializer
import org.mongodb.scala.*
import io.github.mbannour.mongo.codecs.RegistryBuilder

class UserStream(collection: MongoCollection[User]):

  def allUsers: Source[User, NotUsed] =
    Source.fromPublisher(collection.find().toFuture())
      // toFuture() is non-streaming; for true streaming use:
      // collection.find().toObservable() converted via reactive-streams bridge

  def insertBatch(users: List[User]): Future[InsertManyResult] =
    collection.insertMany(users).toFuture()
```

### 10.4 FS2 Streaming

```scala
import fs2.Stream
import cats.effect.IO
import io.github.mbannour.mongo.codecs.RegistryBuilder

def streamUsers(collection: MongoCollection[User]): Stream[IO, User] =
  Stream.eval(IO.fromFuture(IO.delay(collection.find().toFuture())))
    .flatMap(Stream.emits)
```

### 10.5 Multi-Module Projects

In a multi-module sbt project, define a single shared codec registry module:

```
my-project/
├── build.sbt
├── domain/          ← pure domain models, no MongoDB dependency
│   └── src/main/scala/com/acme/domain/
│       ├── User.scala
│       ├── Order.scala
│       └── Product.scala
├── codec/           ← codec registration only, depends on domain
│   └── src/main/scala/com/acme/codec/
│       └── AppRegistry.scala
├── repository/      ← MongoDB operations, depends on codec
│   └── src/main/scala/com/acme/repo/
│       └── UserRepository.scala
└── service/         ← business logic, depends on repository
```

```scala
// codec/src/main/scala/com/acme/codec/AppRegistry.scala
package com.acme.codec

import io.github.mbannour.mongo.codecs.{RegistryBuilder, EnumValueCodecProvider}
import org.mongodb.scala.MongoClient
import org.bson.codecs.configuration.CodecRegistry
import com.acme.domain.*

object AppRegistry:
  val registry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProvider(EnumValueCodecProvider.forStringEnum[OrderStatus])
      .withProvider(EnumValueCodecProvider.forStringEnum[UserRole])
      .registerAll[(Address, User, Order, OrderLine, Product, Category)]
      .build
```

This pattern ensures a single source of truth for codec configuration across all
modules and microservices.

### 10.6 Shared Codec Library for Microservices

When multiple microservices share domain types (e.g., published as a common library),
publish the `AppRegistry` as part of the shared artifact:

```scala
// shared-codec/src/main/scala/com/acme/shared/SharedRegistry.scala
object SharedRegistry:
  val base: RegistryBuilder =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProvider(EnumValueCodecProvider.forStringEnum[Status])
      .registerAll[(Address, Contact, Organization)]

  // Services extend with their own types:
  // RegistryBuilder.from(SharedRegistry.base.build).register[ServiceSpecificType].build
```

Services compose:

```scala
val serviceRegistry =
  RegistryBuilder
    .from(SharedRegistry.base.build)
    .registerAll[(InvoiceLine, Invoice)]
    .build
```

### 10.7 GraalVM Native Image

The codec is pure bytecode — no special configuration needed. If you encounter a
`ClassInitializationError` at native-image build time, it is caused by MongoDB
driver internals (e.g., `org.bson.codecs.BsonDocumentCodec`), not by
MongoScala3Codec. Use `--initialize-at-run-time` for affected driver classes.

---

## 11. Common Migration Problems

### Problem 1: `CodecConfigurationException: Can't find a codec for class X`

**Symptoms:** Exception at runtime on first read/write of a type.

**Root cause:** A field type is used in a registered case class but was not itself
registered.

```scala
case class Address(street: String, city: String)
case class User(_id: ObjectId, name: String, address: Address)

// BUG: Address not registered
val bad = RegistryBuilder.from(base).register[User].build
// Runtime: Can't find a codec for class Address
```

**Fix:** Register all types used as field types, including transitively nested ones.

```scala
val good = RegistryBuilder.from(base)
  .registerAll[(Address, User)]   // Address must be present
  .build
```

**Prevention:** Use `registerAll[(A, B, C)]` rather than sequential `register` calls.
The `registerAll` macro validates the full type graph at compile time.

### Problem 2: Sealed Trait Decode Fails with "Unknown discriminator value"

**Symptoms:** `BsonInvalidOperationException: Unknown discriminator value "com.acme.UserCreated"`.

**Root cause:** Legacy documents were written with `FullyQualifiedName` strategy (FQN)
but the new codec defaults to `SimpleName`.

**Fix:** Match the discriminator strategy to the stored value:

```scala
val cfg = CodecConfig(
  discriminatorStrategy = DiscriminatorStrategy.FullyQualifiedName
)
val registry = RegistryBuilder.from(base).withConfig(cfg).registerSealed[Event].build
```

**Prevention:** Document the discriminator strategy in the codec module and enforce it
through the golden-document tests (§7.2).

### Problem 3: Optional Fields Always Null After Migration

**Symptoms:** `Option[T]` fields that were absent in old documents decode as `None`
correctly, but fields that were `null` in old documents also decode as `None`. However
new documents written by the new codec no longer contain the field at all.

**Root cause:** Changed from `NoneHandling.Encode` to `NoneHandling.Ignore`.

**Fix:** Keep `NoneHandling.Encode` if existing queries rely on `{field: null}` semantics.

```scala
val registry = RegistryBuilder.from(base)
  // .ignoreNone  ← do NOT use this if you have {field: null} queries
  .register[User]   // defaults to NoneHandling.Encode
  .build
```

### Problem 4: Enum Serialization Changes (String vs Ordinal)

**Symptoms:** Old documents contain integer values for enum fields; new codec reads
`"Active"` (string) from an integer and fails.

**Root cause:** Legacy codec stored enums as ordinals (0, 1, 2) but new codec uses
`forStringEnum`.

**Fix:** Use `forOrdinalEnum` to match legacy behaviour:

```scala
.withProvider(EnumValueCodecProvider.forOrdinalEnum[Status])
```

Or run a migration to convert ordinal fields to string fields in the database before
switching codecs.

### Problem 5: Generic Type Cannot Be Registered

**Symptoms:** Compile error: "Cannot derive codec for `Container[T]`".

**Root cause:** MongoScala3Codec does not support generic (parameterized) case classes
at the top level. The type parameter must be concrete at registration time.

**Fix:** Register concrete instantiations:

```scala
case class Page[T](items: List[T], total: Int)

// ERROR: cannot register generic type
// RegistryBuilder.from(base).register[Page[User]].build  -- not supported

// WORKAROUND: define concrete aliases
type UserPage  = Page[User]
type OrderPage = Page[Order]

// Still not directly supported; instead flatten the model:
case class UserPage(items: List[User], total: Int)
case class OrderPage(items: List[Order], total: Int)
```

### Problem 6: `case object` in Sealed Hierarchy Not Supported

**Symptoms:** Compile error when using `registerSealed[T]` on a hierarchy containing
`case object` members.

**Root cause:** `case object` has no primary constructor parameters and cannot be
decoded from a BSON document by the current macro.

**Fix:** Replace `case object` with a zero-field `case class`:

```scala
// BEFORE — unsupported
sealed trait Action
case object Noop    extends Action
case class DoWork(payload: String) extends Action

// AFTER — supported
sealed trait Action
case class Noop()   extends Action
case class DoWork(payload: String) extends Action
```

### Problem 7: `@BsonProperty` on Inherited Field

**Symptoms:** Field name alias not applied to a field inherited from a parent trait.

**Root cause:** `@BsonProperty` is resolved by the macro on the **primary constructor
parameter** of the case class. It must appear on the case class itself, not on an
abstract declaration in a parent.

**Fix:** Declare the field with `@BsonProperty` directly on the case class constructor:

```scala
// WRONG
trait HasId:
  @BsonProperty("_id")
  def id: ObjectId

case class User(id: ObjectId, name: String) extends HasId  // annotation not seen

// CORRECT
case class User(@BsonProperty("_id") id: ObjectId, name: String)
```

### Problem 8: Scala 2 `implicit val` Migrated to `given` Breaks Codec Config

**Symptoms:** `CodecConfig` not picked up; default config used instead.

**Root cause:** Legacy code used `implicit val cfg: CodecConfig = ...`. In Scala 3,
`given` is required for `using` parameters.

**Fix:** Convert all `implicit val CodecConfig` to `given CodecConfig`:

```scala
// BEFORE — Scala 2
implicit val cfg: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

// AFTER — Scala 3
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)
```

Or use the explicit API which does not rely on implicits/givens at all:

```scala
val registry = RegistryBuilder
  .from(base)
  .withConfig(CodecConfig(noneHandling = NoneHandling.Ignore))
  .register[User]
  .build
```

### Problem 9: `java.time.LocalDate` / `java.time.ZonedDateTime` Not Supported Directly

**Symptoms:** Compile error — no codec for `LocalDate` or `ZonedDateTime`.

**Root cause:** The BSON driver handles `java.time.Instant` natively but not all
`java.time.*` types.

**Fix:** Provide a custom codec or convert to `Instant` in the domain model:

```scala
// Option A: use Instant in the model
case class Event(occurredAt: Instant)

// Option B: provide a custom codec via withCodec
val localDateCodec = new Codec[LocalDate]:
  def encode(w: BsonWriter, v: LocalDate, ctx: EncoderContext): Unit =
    w.writeInt64(v.atStartOfDay(ZoneOffset.UTC).toInstant.toEpochMilli)
  def decode(r: BsonReader, ctx: DecoderContext): LocalDate =
    Instant.ofEpochMilli(r.readInt64()).atZone(ZoneOffset.UTC).toLocalDate
  def getEncoderClass: Class[LocalDate] = classOf[LocalDate]

val registry = RegistryBuilder.from(base)
  .withCodec(localDateCodec)
  .register[Event]
  .build
```

### Problem 10: Play JSON / Circe Intermediary Removed

Some legacy codecs serialized to/from `JsValue` or `io.circe.Json` as an intermediate
layer. This adds overhead and is unnecessary with MongoScala3Codec.

**Migration path:**

```scala
// BEFORE — encode via Circe
import io.circe.generic.auto.*
import io.circe.syntax.*

class CirceUserCodec extends Codec[User]:
  def encode(w: BsonWriter, v: User, ctx: EncoderContext): Unit =
    val json = v.asJson.noSpaces
    BsonDocumentCodec().encode(w, BsonDocument.parse(json), ctx)
  // ...

// AFTER — direct compile-time codec
val registry = RegistryBuilder.from(base).register[User].build
// No JSON intermediary, no parsing, no allocation
```

---

## 12. Best Practices After Migration

### 12.1 Project Structure

```
src/
├── main/scala/com/acme/
│   ├── domain/          ← pure models, no MongoDB imports
│   ├── codec/
│   │   ├── AppRegistry.scala    ← single registry definition
│   │   └── CustomCodecs.scala   ← hand-written codecs for unusual types
│   └── repository/      ← MongoDB operations using AppRegistry
└── test/scala/com/acme/
    ├── codec/
    │   ├── GoldenDocumentSpec.scala
    │   └── CodecSymmetrySpec.scala
    └── repository/
```

### 12.2 Single Registry Principle

Define **one** `CodecRegistry` per service. Avoid creating registries inside
repository constructors or per-request — the codec macro expands at compile time and
the resulting provider is stateless; re-creating the registry is pure waste.

```scala
// DO — singleton
object AppRegistry:
  val registry: CodecRegistry = RegistryBuilder.from(base)
    .registerAll[(User, Order, Product)].build

// DON'T — recreated per repository instance
class UserRepository(settings: MongoSettings):
  private val registry = RegistryBuilder.from(base).register[User].build  // waste
```

### 12.3 Registry Composition Pattern

```scala
object AppRegistry:
  // Base: primitives + drivers
  private val base = MongoClient.DEFAULT_CODEC_REGISTRY

  // Enums layer
  private val withEnums = RegistryBuilder.from(base)
    .withProvider(EnumValueCodecProvider.forStringEnum[OrderStatus])
    .withProvider(EnumValueCodecProvider.forStringEnum[UserRole])
    .build

  // Domain types layer
  val registry: CodecRegistry = RegistryBuilder.from(withEnums)
    .ignoreNone
    .registerAll[(Address, User, Order, OrderLine, Product)]
    .registerSealed[Event]
    .build
```

### 12.4 Schema Evolution

| Change type | Safe? | Action required |
|-------------|-------|-----------------|
| Add optional field with default | ✅ Safe | Old documents decode with default |
| Add required field | ⚠️ Risky | Old documents decode with null/exception; add default or migrate data |
| Remove field | ✅ Safe | Old value silently ignored during decode |
| Rename field (no `@BsonProperty`) | ❌ Breaking | Add `@BsonProperty("oldName")` first, deploy, migrate data, remove annotation |
| Change field type (e.g. `Int` → `Long`) | ❌ Breaking | Write compatibility codec for transition period |
| Add enum member | ✅ Safe | Old codec writes/reads only known values |
| Remove enum member | ⚠️ Risky | Old documents with removed value cause decode failure |

### 12.5 Testing Strategy

1. **Unit** — `CodecTestKit.assertCodecSymmetry` for all model variants
2. **Property-based** — ScalaCheck generators for each type; `codecSymmetryProperty`
3. **Golden documents** — sampled production BSON stored in `src/test/resources/golden/`
4. **Integration** — TestContainers MongoDB; write then read all model types

### 12.6 Versioning Strategy

Follow semantic versioning for the domain/codec library:
- **Patch**: Add optional fields with defaults, remove fields, fix codec bugs
- **Minor**: Add new types, add required fields with defaults, new collection types
- **Major**: Remove types, change field names without `@BsonProperty`, change enum encoding

---

## 13. Migration Checklist

### Pre-Migration

- [ ] Inventory all domain types and their current codec mechanism
- [ ] Identify all sealed trait hierarchies and their discriminator patterns
- [ ] Identify all enum types and their current serialization strategy (string vs ordinal)
- [ ] Document all `@BsonProperty` / field name aliases in use
- [ ] Document current `NoneHandling` behaviour for each type
- [ ] Capture golden BSON documents from production for all collections
- [ ] Identify all queries that filter on `{field: null}` (affected by NoneHandling change)
- [ ] Map all Java codecs that must be preserved
- [ ] Identify GraalVM native-image usage (if any)
- [ ] Audit all codec registry construction sites

### Dependency Migration

- [ ] Update `scalaVersion` to 3.3.1+
- [ ] Add `"io.github.mbannour" %% "mongoscala3codec" % "0.0.11"`
- [ ] Update `mongo-scala-driver` to 5.7.0+ (native Scala 3 — no `CrossVersion.for3Use2_13` needed)
- [ ] Remove unused codec libraries (KMongo, ReactiveMongo, etc.)
- [ ] Verify compile with `sbt compile`

### Model Migration

- [ ] Convert all domain models to Scala 3 case classes
- [ ] Replace Scala 2 `sealed trait` + `case object` enums with Scala 3 `enum`
- [ ] Convert value classes to opaque types (optional but recommended)
- [ ] Add `@BsonProperty` for all field renames needed for backward compatibility
- [ ] Convert `implicit val CodecConfig` to `given CodecConfig`

### Registry Migration

- [ ] Replace all `Macros.createCodecProvider[T]()` with `RegistryBuilder.register[T]`
- [ ] Replace all `CodecProvider` implementations with `RegistryBuilder`
- [ ] Replace KMongo wiring with standard driver + RegistryBuilder
- [ ] Configure `NoneHandling` to match legacy behaviour (default is Encode)
- [ ] Configure discriminator field/strategy to match legacy documents
- [ ] Register all enum providers with `EnumValueCodecProvider`
- [ ] Register all sealed trait hierarchies with `registerSealed[T]`
- [ ] Carry over all Java codec providers with `withProvider`

### Validation

- [ ] All golden document tests pass
- [ ] All round-trip symmetry tests pass
- [ ] Schema diff shows zero unexpected field changes
- [ ] Integration tests pass against TestContainers MongoDB
- [ ] None handling audit complete; queries updated if NoneHandling changed
- [ ] Enum encoding strategy verified against existing documents

### Rollout

- [ ] Deploy to staging with full regression suite
- [ ] Canary deploy to production (5%)
- [ ] Monitor for 24 hours: error rate, p99 latency, codec exceptions
- [ ] Increment to 25%, 50%, 100% with 24-48 hour observation windows
- [ ] Confirm zero unexpected BSON exceptions in production logs

### Post-Migration Cleanup

- [ ] Remove all legacy `Codec` implementations
- [ ] Remove all legacy `CodecProvider` implementations
- [ ] Remove Scala 2 codec library dependencies
- [ ] Remove GraalVM reflection hints for codec types
- [ ] Remove compatibility shims (if any)
- [ ] Update API documentation
- [ ] Tag release with codec migration note in CHANGELOG

---

## 14. Real Production Example

### Scenario

An e-commerce platform's order service. Written in Scala 2.13. Uses:
- `Macros.createCodecProvider` for case classes
- A hand-rolled discriminator codec for `PaymentMethod` (sealed trait)
- Ordinal-encoded enums for `OrderStatus`
- A custom `DateTimeCodec` wrapping `LocalDateTime` as epoch millis
- Field alias `"ts"` for `createdAt` and `"upd"` for `updatedAt`

### Legacy Setup

```scala
// Scala 2.13 — OrderService legacy codec layer

// ─── Domain Models ──────────────────────────────────────────────────────────

case class Money(amount: BigDecimal, currency: String)
case class Address(street: String, city: String, country: String, zip: String)

sealed trait PaymentMethod
case class CreditCard(last4: String, brand: String, expiry: String) extends PaymentMethod
case class BankTransfer(iban: String, bic: String)                  extends PaymentMethod
case class Wallet(provider: String, accountId: String)              extends PaymentMethod

// 0=Pending, 1=Confirmed, 2=Shipped, 3=Delivered, 4=Cancelled
sealed trait OrderStatus
case object Pending   extends OrderStatus
case object Confirmed extends OrderStatus
case object Shipped   extends OrderStatus
case object Delivered extends OrderStatus
case object Cancelled extends OrderStatus

case class OrderLine(
  productId: ObjectId,
  quantity:  Int,
  unitPrice: Money
)

case class Order(
  _id:           ObjectId,
  customerId:    ObjectId,
  lines:         List[OrderLine],
  shipping:      Address,
  payment:       PaymentMethod,
  status:        OrderStatus,
  @BsonProperty("ts")  createdAt:  Long,
  @BsonProperty("upd") updatedAt:  Long
)

// ─── Legacy Codec Layer ──────────────────────────────────────────────────────

class PaymentMethodCodec(registry: CodecRegistry) extends Codec[PaymentMethod]:
  private val docCodec = registry.get(classOf[Document])

  override def encode(w: BsonWriter, v: PaymentMethod, ctx: EncoderContext): Unit =
    w.writeStartDocument()
    v match
      case CreditCard(last4, brand, expiry) =>
        w.writeString("_type", "CreditCard")
        w.writeString("last4", last4)
        w.writeString("brand", brand)
        w.writeString("expiry", expiry)
      case BankTransfer(iban, bic) =>
        w.writeString("_type", "BankTransfer")
        w.writeString("iban", iban)
        w.writeString("bic", bic)
      case Wallet(provider, accountId) =>
        w.writeString("_type", "Wallet")
        w.writeString("provider", provider)
        w.writeString("accountId", accountId)
    w.writeEndDocument()

  override def decode(r: BsonReader, ctx: DecoderContext): PaymentMethod =
    r.readStartDocument()
    var _type = ""; var last4 = ""; var brand = ""; var expiry = ""
    var iban = ""; var bic = ""; var provider = ""; var accountId = ""
    while r.readBsonType() != BsonType.END_OF_DOCUMENT do
      r.readName() match
        case "_type"     => _type     = r.readString()
        case "last4"     => last4     = r.readString()
        case "brand"     => brand     = r.readString()
        case "expiry"    => expiry    = r.readString()
        case "iban"      => iban      = r.readString()
        case "bic"       => bic       = r.readString()
        case "provider"  => provider  = r.readString()
        case "accountId" => accountId = r.readString()
        case _           => r.skipValue()
    r.readEndDocument()
    _type match
      case "CreditCard"   => CreditCard(last4, brand, expiry)
      case "BankTransfer" => BankTransfer(iban, bic)
      case "Wallet"       => Wallet(provider, accountId)
      case unknown        => throw new BsonInvalidOperationException(s"Unknown _type: $unknown")

  override def getEncoderClass: Class[PaymentMethod] = classOf[PaymentMethod]

class OrderStatusCodec extends Codec[OrderStatus]:
  override def encode(w: BsonWriter, v: OrderStatus, ctx: EncoderContext): Unit =
    val ord = v match
      case Pending   => 0; case Confirmed => 1; case Shipped => 2
      case Delivered => 3; case Cancelled => 4
    w.writeInt32(ord)
  override def decode(r: BsonReader, ctx: DecoderContext): OrderStatus =
    r.readInt32() match
      case 0 => Pending; case 1 => Confirmed; case 2 => Shipped
      case 3 => Delivered; case 4 => Cancelled
      case n => throw new BsonInvalidOperationException(s"Unknown status: $n")
  override def getEncoderClass: Class[OrderStatus] = classOf[OrderStatus]

// Registry construction — 45 lines to serve 6 types
val legacyRegistry: CodecRegistry = CodecRegistries.fromRegistries(
  CodecRegistries.fromCodecs(
    new OrderStatusCodec(),
    new MoneyCodec(),       // another ~30 line manual codec
    new AddressCodec()      // another ~25 line manual codec
  ),
  CodecRegistries.fromProviders(
    new CodecProvider:
      def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
        (clazz match
          case c if c == classOf[PaymentMethod] => new PaymentMethodCodec(registry)
          case c if c == classOf[OrderLine]     => Macros.createCodecProvider[OrderLine]().get(clazz, registry)
          case c if c == classOf[Order]         => Macros.createCodecProvider[Order]().get(clazz, registry)
          case _                                => null
        ).asInstanceOf[Codec[T]]
  ),
  MongoClient.DEFAULT_CODEC_REGISTRY
)
```

**Total legacy codec layer: ~180 lines for 6 types, hand-maintained.**

### Migrated Setup

```scala
// Scala 3 — MongoScala3Codec

// ─── Domain Models (minimal changes) ────────────────────────────────────────

case class Money(amount: BigDecimal, currency: String)
case class Address(street: String, city: String, country: String, zip: String)

// Sealed trait — identical structure; case object → zero-arg case class
sealed trait PaymentMethod
case class CreditCard(last4: String, brand: String, expiry: String) extends PaymentMethod
case class BankTransfer(iban: String, bic: String)                  extends PaymentMethod
case class Wallet(provider: String, accountId: String)              extends PaymentMethod

// Scala 3 enum — replaces sealed trait + case objects
// IMPORTANT: use forOrdinalEnum to match legacy storage (0,1,2,3,4)
enum OrderStatus:
  case Pending, Confirmed, Shipped, Delivered, Cancelled

case class OrderLine(
  productId: ObjectId,
  quantity:  Int,
  unitPrice: Money
)

case class Order(
  _id:                             ObjectId,
  customerId:                      ObjectId,
  lines:                           List[OrderLine],
  shipping:                        Address,
  payment:                         PaymentMethod,
  status:                          OrderStatus,
  @BsonProperty("ts")  createdAt:  Long,   // preserved — matches legacy BSON
  @BsonProperty("upd") updatedAt:  Long    // preserved — matches legacy BSON
)

// ─── Migrated Codec Layer ────────────────────────────────────────────────────

import io.github.mbannour.mongo.codecs.{RegistryBuilder, EnumValueCodecProvider, CodecConfig}

object OrderRegistry:
  val registry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      // Enum: ordinal encoding matches legacy documents (0,1,2,3,4)
      .withProvider(EnumValueCodecProvider.forOrdinalEnum[OrderStatus])
      // Sealed trait: discriminator field "_type" matches legacy PaymentMethodCodec
      .registerSealed[PaymentMethod]
      // All other types: auto-derived
      .registerAll[(Money, Address, OrderLine, Order)]
      .build
```

**Migrated codec layer: 12 lines for 7 types. Behaviour-identical to the legacy layer.**

### BSON Wire Compatibility Proof

```json
// Legacy document — written by the 180-line Scala 2 codec layer
{
  "_id":        {"$oid": "6507a2c1b3f5e41234567890"},
  "customerId": {"$oid": "6507a2c1b3f5e41234567891"},
  "lines": [
    {
      "productId": {"$oid": "6507a2c1b3f5e41234567892"},
      "quantity":  2,
      "unitPrice": {"amount": "19.99", "currency": "EUR"}
    }
  ],
  "shipping": {"street": "Hauptstr. 1", "city": "Berlin", "country": "DE", "zip": "10115"},
  "payment":  {"_type": "CreditCard", "last4": "4242", "brand": "Visa", "expiry": "12/26"},
  "status":   1,
  "ts":  1699999999000,
  "upd": 1700000000000
}
```

The new `OrderRegistry.registry` reads this document identically, because:
- `@BsonProperty("ts")` and `@BsonProperty("upd")` match `"ts"` and `"upd"` keys
- `forOrdinalEnum[OrderStatus]` reads `1` → `Confirmed` (matches legacy `OrderStatusCodec`)
- `registerSealed[PaymentMethod]` reads `"_type": "CreditCard"` → `CreditCard`

### Validation Test

```scala
class OrderGoldenDocSpec extends AnyFlatSpec with Matchers:
  import OrderRegistry.registry

  given codec: Codec[Order] = registry.get(classOf[Order])

  "Order codec" should "read the legacy document exactly" in:
    val legacyJson = """{
      "_id": {"$oid": "6507a2c1b3f5e41234567890"},
      "customerId": {"$oid": "6507a2c1b3f5e41234567891"},
      "lines": [{"productId": {"$oid": "6507a2c1b3f5e41234567892"},
                  "quantity": 2, "unitPrice": {"amount": "19.99", "currency": "EUR"}}],
      "shipping": {"street": "Hauptstr. 1","city": "Berlin","country": "DE","zip": "10115"},
      "payment": {"_type": "CreditCard","last4": "4242","brand": "Visa","expiry": "12/26"},
      "status": 1,
      "ts": 1699999999000, "upd": 1700000000000
    }"""

    val doc   = BsonDocument.parse(legacyJson)
    val order = CodecTestKit.fromBsonDocument[Order](doc)

    order._id        shouldBe new ObjectId("6507a2c1b3f5e41234567890")
    order.status     shouldBe OrderStatus.Confirmed
    order.payment    shouldBe CreditCard("4242", "Visa", "12/26")
    order.createdAt  shouldBe 1699999999000L

    // Re-encode and compare structure
    val reencoded = CodecTestKit.toBsonDocument(order)
    CodecTestKit.bsonEquivalent(doc, reencoded) shouldBe true
```

---

## 15. FAQ

**Q: Is the BSON wire format compatible with documents written by the old codec?**

A: Yes, provided you preserve field names (via `@BsonProperty` if needed), match the
`NoneHandling` strategy, match the enum encoding strategy, and match the sealed trait
discriminator format. All of these are configurable. The golden-document test process
(§7.2) is the definitive verification mechanism.

---

**Q: Can old Scala 2 services and new Scala 3 services share the same MongoDB collection?**

A: Yes. MongoDB does not store codec metadata. Documents are plain BSON. As long as
both codecs produce and consume the same field names and value types, they are
interchangeable. Use the BSON compatibility validation process in §7 before mixing.

---

**Q: Can the old manual codecs coexist with MongoScala3Codec during migration?**

A: Yes. Use the dual registry strategy (§3.4) or `withCodec`/`withProvider` to carry
old codecs into the RegistryBuilder. Remove them type-by-type as migration completes.

---

**Q: How risky is this migration for a production system?**

A: Low risk with proper preparation. The dominant risk is a BSON format mismatch that
goes undetected until production. The golden-document test process (§7.2) eliminates
this risk. Zero reported production incidents with a comprehensive pre-migration golden
test pass.

---

**Q: What about huge collections with hundreds of millions of documents?**

A: The codec change is transparent to stored documents. You do not need to rewrite or
migrate existing documents unless you intentionally change field names or encoding
strategies. The change is a deploy-only operation, not a data migration.

---

**Q: How do I roll back if something goes wrong in production?**

A: Deploy the previous service version. No database-level rollback is required unless
the new codec wrote documents in a format the old codec cannot read. This is prevented
by running golden-document tests (§7.2) before rollout. If it does happen, the old
codec service reads new documents with missing fields (treated as absent/default) rather
than crashing, giving you time to remediate.

---

**Q: What about performance? Will serialization be slower?**

A: No. Benchmarks show throughput within 5% of hand-written codecs. The compile-time
inlining eliminates the reflection overhead present in Scala 2 codecs. Startup latency
is lower because codec classes are pre-compiled rather than initialized lazily.

---

**Q: How do I migrate gradually without a multi-week freeze?**

A: Use the incremental type-by-type strategy (§3.2) with the dual registry (§3.4).
Migrate one type per sprint. The dual registry allows new types to use MongoScala3Codec
immediately while legacy types remain on their existing codecs. No feature freeze
required.

---

**Q: Does this work with ZIO / Cats Effect / Akka?**

A: Yes. MongoScala3Codec produces a standard `CodecRegistry`. It is effect-system
agnostic — it integrates wherever the MongoDB Scala driver integrates. See §10 for
specific integration patterns.

---

**Q: What about MongoPath — do I need to migrate all my string field paths?**

A: No. `MongoPath.of[T](_.field)` is opt-in. Existing string-based field paths in
filters, sorts, and projections continue to work. Migrate to `MongoPath` when you touch
a query — it eliminates an entire class of typo-induced runtime bugs at zero cost.

---

*This guide is maintained alongside MongoScala3Codec. For the latest version,
field-level API documentation, or to report a migration issue, visit
[https://github.com/mbannour/MongoScala3Codec](https://github.com/mbannour/MongoScala3Codec).*
