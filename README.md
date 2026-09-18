# MongoScala3Codec

**Type-safe MongoDB for Scala 3.**

Derive native MongoDB `Codec[T]` instances for your Scala models, with the field mapping checked
at compile time, the stored BSON written down explicitly, and no JSON step in between.

Built on the official MongoDB BSON and codec APIs. **It does not replace the MongoDB driver** — you
keep using `MongoClient`, `MongoCollection`, `Filters` and the rest exactly as you do today.

![version](https://img.shields.io/badge/release-0.0.11-brightgreen)
![Scala](https://img.shields.io/badge/Scala-3.3.1%2B-blue)
![Build Status](https://github.com/mbannour/MongoScala3Codec/workflows/Test%20Scala%20Library/badge.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## Contents

- [Why MongoScala3Codec?](#why-mongoscala3codec)
- [Quick start](#quick-start)
- [Deriving codecs](#deriving-codecs)
- [Supported model features](#supported-model-features)
- [BSON annotations](#bson-annotations)
- [Sealed traits and discriminators](#sealed-traits-and-discriminators)
- [Scala 3 enums](#scala-3-enums)
- [Recursive models](#recursive-models)
- [Custom codec interoperability](#custom-codec-interoperability)
- [Type-safe field paths](#type-safe-field-paths)
- [Testing codecs with CodecTestKit](#testing-codecs-with-codectestkit)
- [What we promise about compatibility](#what-we-promise-about-compatibility)
- [Schema evolution](#schema-evolution)
- [Supported Scala versions](#supported-scala-versions)
- [Supported MongoDB driver versions](#supported-mongodb-driver-versions)
- [Limitations](#limitations)
- [Roadmap](#roadmap)
- [Development](#development)
- [Contributing and license](#contributing-and-license)

---

## Why MongoScala3Codec?

You are storing Scala case classes in MongoDB. Somebody has to decide which BSON field each Scala
field becomes, and what happens when the model changes but the documents in the collection do not.

MongoScala3Codec makes those decisions **at compile time**, and writes them down:

- **Derivation, not boilerplate.** `register[User]` produces a real `org.bson.codecs.Codec[User]`.
  No hand-written codecs, no runtime reflection.
- **Mistakes become compile errors.** A field type derivation cannot handle, a `Map` with non-`String`
  keys, two `@BsonId` fields, `@BsonIgnore` on a field with no default, a duplicate discriminator
  value — each is a compile error naming the model, the field and the fix.
- **The stored shape is explicit.** Every supported model feature has a frozen BSON representation,
  asserted in the test suite against a fixed expected document, and documented below.
- **No JSON in the middle.** The path is `case class` → `Codec[T]` → BSON → driver. Nothing is
  serialized to JSON and re-parsed on the way to the database.
- **No new client, no new effect system.** Output is `Codec[T]` and `CodecRegistry`, which is what
  the official driver already consumes. The core needs neither Cats Effect nor ZIO.

**Relationship to the official driver:** MongoScala3Codec is a layer *on top of* the official
MongoDB BSON and codec APIs — `org.bson.codecs.Codec`, `CodecRegistry`, `BsonReader`/`BsonWriter`.
It adds Scala 3 derivation and compile-time checking on that model. Your codecs are ordinary
MongoDB codecs; they compose with codecs MongoDB ships and with codecs you write by hand.

> **Scope of 1.0.** 1.0 is a codec foundation: derivation, BSON mapping and the compatibility
> contract. Compile-time-checked *filters and updates* are planned for 1.1 — see
> [Roadmap](#roadmap). Nothing in this README describes an API that does not exist today.

---

## Quick start

**1.0.0 is not released yet.** The latest published release is `0.0.11`:

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.11"
```

Together with the official MongoDB Scala driver, which you use for everything else:

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.11",
  ("org.mongodb.scala" %% "mongo-scala-driver" % "5.6.0").cross(CrossVersion.for3Use2_13)
)
```

**Requirements:** Scala 3.3.1 or later, JDK 11 or later.

The library itself depends only on `org.mongodb.scala:mongo-scala-bson` (and, through it,
`org.mongodb:bson`). It does not pull in a MongoDB client — you choose that.

A first program:

```scala
import io.github.mbannour.bson.macros.BsonId
import io.github.mbannour.mongo.codecs.RegistryBuilder
import io.github.mbannour.mongo.codecs.RegistryBuilder.*
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.annotations.BsonProperty

case class User(
    @BsonId id: ObjectId,
    @BsonProperty("email_address") email: String,
    active: Boolean = true
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build

val client = MongoClient("mongodb://localhost:27017")
val users = client
  .getDatabase("myapp")
  .withCodecRegistry(registry)
  .getCollection[User]("users")

users.insertOne(User(new ObjectId(), "ada@example.com")).toFuture()
```

The document that reaches MongoDB:

```json
{
  "_id": { "$oid": "..." },
  "email_address": "ada@example.com",
  "active": true
}
```

Two things are worth noticing. `@BsonId` moved the field to `_id`, and `@BsonProperty` pinned the
persisted name — so you can rename `email` in Scala later without touching a single stored document.

---

## Deriving codecs

`RegistryBuilder` wraps a base `CodecRegistry`, adds derived codecs, and hands back a
`CodecRegistry` the driver understands.

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)   // any CodecRegistry
  .register[Address]
  .register[Person]
  .build
```

Registering several types in one call is cheaper to compile than a chain of `register`:

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerAll[(Address, Person, Task)]
  .build
```

Other operations on the builder:

| Call | Does |
|---|---|
| `register[T]` | derives and adds a codec for `T` |
| `registerAll[(A, B, …)]` | derives several in one macro expansion |
| `registerSealed[T]` | derives a discriminated codec for a sealed hierarchy and all its subtypes |
| `registerSealedAll[(A, B, …)]` | the same, for several hierarchies |
| `registerIf[T](condition)` | registers only when `condition` is true |
| `just[T]` / `withTypes[(A, B)]` | register and `build` in one step |
| `withCodec(codec)` / `withCodecs(…)` | adds hand-written `Codec` instances |
| `withProvider(p)` / `withProviders(…)` | adds `CodecProvider` instances |
| `ignoreNone` / `encodeNone` | chooses how `None` is written (see below) |
| `configure(_.withDiscriminatorField("_class"))` | renames the discriminator field |
| `withConfig(config)` | replaces the whole `CodecConfig` |
| `a ++ b` | merges two builders (config and base come from the left) |
| `build` / `toRegistry` | produces the `CodecRegistry` |

A derived codec is an ordinary MongoDB codec, so you can also take it out and use it directly:

```scala
val codec: org.bson.codecs.Codec[User] = registry.get(classOf[User])
```

### `Option` and `None`

`Some(v)` always writes `v`. What `None` writes is your choice, made on the builder:

| Builder call | `None` becomes |
|---|---|
| `.encodeNone` (**default**) | an explicit BSON `null` |
| `.ignoreNone` | the field is omitted from the document |

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .registerAll[(Address, Person)]
  .build
```

On the way back in, **both** shapes decode to `None`: a missing field and a stored `null` are both
read as `None`. So switching this setting is safe for readers; it changes only what new writes look
like.

> Configuration must reach the **builder**. A `given CodecConfig` sitting in scope does nothing on
> its own — `RegistryBuilder` does not summon it. Either call `ignoreNone`/`encodeNone`/`configure`
> on the builder, or pass the config explicitly with `withConfig(summon[CodecConfig])`. (The
> `given` is summoned implicitly only by `BsonCodec.derived[T]`.)

---

## Supported model features

This is the tested v1.0 contract. Field types outside it are not rejected out of hand: derivation
hands any unknown field type to whatever `Codec` the registry holds for that class, which is how
MongoDB's own codecs and your own plug in. The consequence is that an unsupported field type
compiles and then fails on first encode, which is why the table distinguishes the two.

### Supported

| Type | Stored as |
|---|---|
| `Boolean` | Boolean |
| `Int`, `Short`, `Byte` | Int32 |
| `Long` | Int64 |
| `Double`, `Float` | Double (a `Float` widens) |
| `Char` | Int32 of the code point |
| `String` | String |
| `java.util.UUID` | **String** (not Binary) |
| `org.bson.types.ObjectId` | ObjectId |
| `Option[T]` | `T`, or null/absent — see above |
| `List[T]`, `Seq[T]`, `Vector[T]`, `Set[T]` | Array |
| `Map[String, V]` | embedded document |
| case classes | embedded document, any depth |
| sealed traits / sealed classes | embedded document with a discriminator |
| Scala 3 `enum` | String or Int32, via `EnumValueCodecProvider` |
| self-recursive case classes | nested documents — see [Recursive models](#recursive-models) |

### Supported by delegation to the registry

These are not written by derivation; they reach a codec the registry already holds, and that codec
decides the stored shape. They work, and this library does not freeze their representation.

| Type | Stored as | Codec responsible |
|---|---|---|
| `Array[Byte]` | Binary, subtype 0 | the driver's byte-array codec |
| `java.math.BigDecimal` | Decimal128 | the driver's decimal codec |
| `java.util.Date`, `java.time.Instant`, `LocalDate`, `LocalDateTime` | UTC datetime | the driver's value / JSR-310 providers |
| opaque type aliases | as the underlying type | the underlying type's codec |
| any type you have a `Codec` for | whatever your codec writes | your codec |

For the JSR-310 types, the provider must actually be in your base registry.
`MongoClient.DEFAULT_CODEC_REGISTRY` includes it.

### Rejected at compile time

| Shape | Diagnostic names |
|---|---|
| `Map` with non-`String` keys | the model and that keys must be `String` |
| tuple fields | the model, the field and the type |
| a field type derivation cannot handle and cannot delegate | the model, the field and the type |
| a model whose path is not stable (declared inside a method) | the model and why |
| two `@BsonId` fields, or `@BsonId` together with `@BsonIgnore` | the conflict |
| `@BsonIgnore` on a field with no constructor default | the field |
| duplicate or empty `@BsonDiscriminator` values | the subtypes involved |

### Compiles, then fails on first encode

Characterized so a change becomes visible, not endorsed. There is no codec for these:

- `scala.math.BigDecimal` — use `java.math.BigDecimal`
- `scala.math.BigInt` — no codec exists for it
- `Array[T]` where `T` is not `Byte`

### Not part of the v1.0 contract

- **`Either[L, R]`** — not supported. It derives and then fails on encode. Model the choice as a
  sealed trait instead.
- Case objects as sealed subtypes — use case classes, or a Scala 3 `enum`.
- Sealed hierarchies with type parameters.
- Generic case classes.

---

## BSON annotations

### `@BsonProperty` — pin the persisted name

`org.mongodb.scala.bson.annotations.BsonProperty`, MongoDB's own annotation.

```scala
case class Person(@BsonProperty("full_name") fullName: String, age: Int)
// {"full_name": "Ada Lovelace", "age": 36}
```

It controls the **BSON field name** only; the Scala name is yours. That separation is what makes a
Scala-side rename a non-event for stored documents: keep the annotation value and historical
documents keep decoding. `MongoPath` respects it too.

### `@BsonId` — map a field to `_id`

`io.github.mbannour.bson.macros.BsonId`.

```scala
case class User(@BsonId id: ObjectId, name: String)
// {"_id": {"$oid": "..."}, "name": "..."}
```

Notes:

- The annotation maps the name. It does **not** generate identifiers, and the field does not have
  to be an `ObjectId`.
- A field merely *called* `id` is stored as `"id"`. Only the annotation moves it to `_id`. (A field
  literally named `_id` is stored as `_id`, since that is already its name.)
- At most one `@BsonId` per model, and not on the same field as `@BsonIgnore` — both are compile
  errors.

### `@BsonIgnore` — leave a field out of BSON

`io.github.mbannour.bson.macros.BsonIgnore`. The field needs a constructor default, or it is a
compile error: decoding has to put *something* there.

```scala
case class User(name: String, @BsonIgnore scratch: String = "")
// {"name": "n"}  — scratch is not written
```

**This mapping is deliberately lossy.** Decoding restores the constructor default, not the value you
encoded, so `decode(encode(x))` is not `x` when an ignored field was non-default. That is the
intended behavior, not a defect.

### `@BsonDiscriminator` — choose a subtype's stored value

`io.github.mbannour.bson.macros.BsonDiscriminator`, covered in the next section.

---

## Sealed traits and discriminators

`registerSealed[T]` derives a codec for the hierarchy and for every concrete subtype.

```scala
sealed trait Animal
case class Dog(name: String, breed: String) extends Animal
case class Cat(name: String, lives: Int) extends Animal

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Animal]
  .build
```

The stored representation, frozen:

```json
{"_type": "Dog", "name": "Rex", "breed": "Labrador"}
{"_type": "Cat", "name": "Tom", "lives": 9}
```

- The discriminator field is **`_type`** by default, and is the **first key** in the document — at
  the root and inside nested documents. A reader that streams a document can therefore pick the
  subtype before consuming the payload. (Our own decoder does not require it: a document with
  `_type` last still decodes.)
- The value is the **simple type name**.
- The concrete subtype's own codec writes the discriminator too, so a value encoded as `Dog` is
  still decodable as `Animal`.

### A custom value per subtype

```scala
sealed trait Shape
@BsonDiscriminator("circle") case class Circle(radius: Double) extends Shape
@BsonDiscriminator("square") case class Square(side: Double) extends Shape
// {"_type": "circle", "radius": 1.5}
```

Adding the annotation to a subtype that is already in production **changes what is persisted for
it**, so documents written earlier no longer decode as that subtype. Duplicate effective values and
empty values are both compile errors.

### A custom discriminator field

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .configure(_.withDiscriminatorField("_class"))
  .registerSealed[Animal]
  .build
// {"_class": "Dog", "name": "Rex", "breed": "Labrador"}
```

### Nested and multi-level hierarchies

A sealed trait used as a field nests the discriminator inside that field's document:

```json
{"owner": "Ann", "pet": {"_type": "Dog", "name": "Rex", "breed": "Labrador"}}
```

A multi-level hierarchy is flattened — only the concrete subtype appears, never the intermediate
trait:

```scala
sealed trait Transport
sealed trait Motorized extends Transport
case class Bus(capacity: Int) extends Motorized
case class Bicycle(gears: Int) extends Transport
// {"_type": "Bus", "capacity": 42}
```

---

## Scala 3 enums

Enums go through `EnumValueCodecProvider`, in one of two modes, which you add to the base registry:

```scala
import io.github.mbannour.mongo.codecs.EnumValueCodecProvider
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}

enum Priority:
  case Low, Medium, High

val base = fromRegistries(
  MongoClient.DEFAULT_CODEC_REGISTRY,
  fromProviders(EnumValueCodecProvider.forStringEnum[Priority])
)

val registry = RegistryBuilder.from(base).register[Task].build
```

| Mode | Stored as | Example |
|---|---|---|
| `forStringEnum[E]` | the case name, as a String | `{"priority": "Medium"}` |
| `forOrdinalEnum[E]` | the case ordinal, as Int32 | `{"priority": 1}` |

Enums declared inside an `object` work as well as top-level ones.

### Ordinal enums and the order of your cases

**With `forOrdinalEnum`, the stored value is a position, and positions move.** Inserting a case
anywhere but the end, or reordering cases, silently changes what every historical document means:

```scala
enum Status:           // v1
  case New, Active, Closed
// a document stored 1 == Active

enum Status:           // v2 — Pending inserted at index 1
  case New, Pending, Active, Closed
// the same document now decodes as Pending
```

Nothing fails. No exception, no warning — the data quietly means something else. An ordinal past the
end of the enum does fail rather than decode, but that is the only case you get told about.

**Use `forStringEnum` unless you have a measured reason not to.** String enums tolerate adding cases
and reordering them freely; only *renaming* a case breaks historical documents, and it breaks
loudly.

Switching an existing field from string to ordinal mode (or back) does not read historical
documents.

---

## Recursive models

A self-referential case class is supported when the recursion passes through `Option`, so a value
is finite:

```scala
case class Node(value: String, next: Option[Node])

val registry = RegistryBuilder.from(MongoClient.DEFAULT_CODEC_REGISTRY).register[Node].build
```

```json
{"value": "a", "next": {"value": "b", "next": {"value": "c", "next": null}}}
```

**Cyclic runtime object graphs are not supported.** BSON documents are trees. A value that points
back at itself has no finite document, and nothing here detects the cycle for you.

---

## Custom codec interoperability

Derivation does not try to own every type. If the registry has a `Codec` for a field's type,
derivation uses it — including for types that *look* derivable, like a case class.

```scala
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}

final case class Money(cents: Long)

class MoneyCodec extends Codec[Money]:
  def encode(writer: BsonWriter, value: Money, ctx: EncoderContext): Unit = writer.writeInt64(value.cents)
  def decode(reader: BsonReader, ctx: DecoderContext): Money = Money(reader.readInt64())
  def getEncoderClass: Class[Money] = classOf[Money]

case class Priced(name: String, price: Money)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withCodec(new MoneyCodec())
  .register[Priced]
  .build
```

`Money` is a case class, and it is still stored as the Int64 its own codec writes:

```json
{"name": "widget", "price": 500}
```

There is no proprietary registration path: a `Codec[T]` from MongoDB, from another library, or from
your own code all register the same way. Nothing traps your models inside this library's
serialization model.

**The boundary of our contract.** Inside your codec, the stored shape and its evolution are yours.
This library cannot promise anything about how a document written by `MoneyCodec` behaves when
`MoneyCodec` changes.

---

## Type-safe field paths

`MongoPath` turns a field access into the BSON path string, at compile time, respecting
`@BsonProperty`:

```scala
import io.github.mbannour.fields.MongoPath
import io.github.mbannour.fields.MongoPath.syntax.?   // hop through Option
import org.mongodb.scala.model.Filters

case class Address(street: String, @BsonProperty("zip") zipCode: Int)
case class User(_id: ObjectId, name: String, address: Option[Address])

MongoPath.of[User](_.address.?.zipCode)   // "address.zip"
MongoPath.of[User](_._id)                 // "_id"

Filters.equal(MongoPath.of[User](_.address.?.zipCode), 12345)
```

Use plain access chains (`_.a.b.c`). `MongoPath.syntax` also provides `each` for traversing
collection fields. The result is a `String`, so it drops straight into `Filters`, `Updates`,
`Sorts` and `Projections` from the official driver.

This is a path, not a typed filter — it checks that the *field exists and spells correctly*, not
that the value you compare it to has the right type. Typed filters are 1.1 work; see
[Roadmap](#roadmap).

---

## Testing codecs with CodecTestKit

`CodecTestKit` drives the same `Codec[T]` your application uses and needs no database and no
particular test framework — assertions throw `AssertionError`, which every Scala test framework
reports as a failure.

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit
import org.bson.{BsonDocument, BsonInt32, BsonString}

case class User(name: String, age: Int)

val kit = CodecTestKit(registry.get(classOf[User]))

val expected = new BsonDocument()
  .append("name", new BsonString("Alice"))
  .append("age", new BsonInt32(37))

kit.assertBson(User("Alice", 37), expected)       // does it encode to this document?
kit.assertDecode(expected, User("Alice", 37))     // does this document decode to this value?
kit.assertRoundTrip(User("Alice", 37))            // does the value survive a round trip?

val doc: BsonDocument = kit.encode(User("Bob", 5)) // or inspect it yourself
val back: User = kit.decode(doc)
```

### Why a round trip is not enough

These answer three different questions, and `assertRoundTrip` answers only the third:

1. **Does my value encode to the document I expect?** — `assertBson`
2. **Does a document already in my database decode to the value I expect?** — `assertDecode`
3. **Does my value survive encoding and decoding?** — `assertRoundTrip`

The first two are wire-compatibility questions and need a document **written by hand**. An encoder
and a decoder can share the same mistake and round-trip perfectly: swap two field names in both and
every round trip still passes, while every document in your database is wrong. Only a hand-written
expected document catches that.

So for any model whose documents outlive a deploy, assert the document — that is the thing your
database actually holds:

```scala
kit.assertBson(
  User("Alice", 37),
  BsonDocument.parse("""{"name": "Alice", "age": 37}""")
)
```

Keeping those assertions next to the model is what makes a later macro change, annotation change or
library upgrade fail your build instead of your production reads.

---

## What we promise about compatibility

There are four different things people mean by "compatible", and they are worth keeping apart.

| Dimension | What it covers | How it is checked |
|---|---|---|
| **BSON wire compatibility** | the documents this library writes and reads | golden BSON fixtures, asserting the exact expected document |
| **Schema evolution** | your model changing while documents stay put | explicit evolution fixtures, see below |
| **Binary compatibility** | dropping a new jar into an existing build | MiMa, on every build |
| **Scala compiler compatibility** | which Scala 3 versions compile it | a CI matrix over Scala versions |
| **MongoDB driver compatibility** | which driver versions it works with | a CI matrix over driver versions |

### BSON wire compatibility

> Within the 1.x line, the BSON representation of a supported model feature does not change. If one
> ever has to, that is handled as a breaking change — documented, and released accordingly.

Every representation shown in this README is asserted in the test suite against a fixed expected
document. This is a promise about **the library**: the same model, on a later 1.x release, produces
the same document and still reads the ones it produced before.

It is **not** a promise about your schema. If you rename a field, change a field's type or reorder
an ordinal enum, that is your change to your documents, and the next section is about what happens.

### Binary compatibility

MiMa runs on every build against a published baseline. Two honest caveats:

- Today's baseline is a `0.x` artifact, which makes the check an **engineering guard**, not a
  promise: 0.x carries no compatibility guarantee. From 1.0.0 on, every 1.x release is checked
  against **1.0.0** — not against its predecessor — so that a symbol dropped in 1.2 cannot go
  unnoticed in 1.3.
- MiMa compares JVM signatures. It does not prove source compatibility, it says nothing about
  inline/macro compatibility, and it says nothing at all about BSON. Those are the other rows of
  the table.

---

## Schema evolution

What happens to documents already in MongoDB when the Scala model moves on. Every row is an
executable test, not advice.

| Change to your model | Status | What happens |
|---|---|---|
| Add a field with a constructor default | **compatible** | historical documents read; the default fills in |
| Add an `Option` field (with or without `= None`) | **compatible** | historical documents read as `None` |
| Add a collection field with a default | **compatible** | reads as the default, not as null |
| Add a required field with no default | **breaking** | historical documents fail to decode, naming the field |
| Add a defaulted field to a *nested* model | **compatible** | reads at every level, recursive models included |
| Add a required field to a nested model | **breaking** | fails, naming the nested field |
| Remove a field from the model | **conditionally compatible** | historical documents still read — but the field is no longer written, so the next write drops the stored value |
| Rename the Scala field, pinning the old name with `@BsonProperty` | **compatible** | reads and keeps writing the historical name |
| Rename the persisted BSON name | **breaking** | historical documents do not read under the new name |
| …when the renamed field is an `Option` | **semantically dangerous** | reads as `None`, silently losing the historical value |
| Widen `Int` → `Long` | **compatible** | historical Int32 values read |
| Narrow `Long` → `Int` | **conditionally compatible** | values that fit read; values that do not fail rather than truncate |
| Change a field's type otherwise (e.g. `Int` → `String`) | **breaking** | historical documents do not read |
| Change a collection's element type | **breaking** | historical arrays do not read |
| Add `@BsonId` to a field | **compatible** | reads documents stored under `_id` |
| Remove `@BsonId` | **breaking** | stops reading `_id` documents |
| Add a `@BsonIgnore` field with a default | **compatible** | reads documents that never contained it |
| Mark an existing persisted field `@BsonIgnore` | **conditionally compatible** | the historical value still reads, but is no longer written — the next write erases it for good |
| Add a subtype to a sealed hierarchy | **compatible** | existing subtypes' documents still read |
| Remove a subtype | **breaking** | documents under the removed discriminator do not read |
| Change the discriminator field name | **breaking** | documents written under the old field do not read |
| Change a subtype's `@BsonDiscriminator` value | **breaking** | documents under the old value do not read |
| Add a case to a string enum | **compatible** | existing case names still read |
| Rename a case of a string enum | **breaking** | documents holding the old name do not read |
| **Insert or reorder cases in an ordinal enum** | **semantically dangerous** | historical ordinals silently decode as the *wrong* case |
| Switch a string enum to ordinal (or back) | **breaking** | historical values do not read |

### Unknown fields are ignored

A document carrying a field the model does not declare **decodes**, and the extra field is ignored
— wherever it sits in the document, and whether it is a scalar or a whole sub-document.

This is what makes a rolling deploy safe in the forward direction: readers still running the old
code can read documents written by the new code. It is also why removing a field from your model is
not immediately fatal.

### One sharp edge worth knowing

A stored **explicit `null`** is not the same as an **absent field**. Absence triggers the
constructor default; a `null` is a value, and on a non-`Option` field it arrives as Scala `null`
(or `0` on a numeric field) — the default is not applied.

This matters most when a field stops being an `Option`, because `encodeNone` — the default — wrote
exactly those nulls. The behavior is characterized in the test suite so a change to it is visible;
it is recorded here rather than presented as desirable.

---

## Supported Scala versions

**Default (compiled and published against): Scala 3.7.4.**

Tested in CI across JDK 11, 17, 19 and 21:

| Scala | Status |
|---|---|
| 3.3.1 | tested |
| 3.4.2 | tested |
| 3.6.4 | tested |
| 3.7.1 | tested |
| 3.7.4 | default |
| 3.8.0 | tested |

Scala 3.3.1 is the floor. Versions between the tested ones are expected to work, but only the ones
listed are tested — we do not claim "all Scala 3 versions".

---

## Supported MongoDB driver versions

**Compiled and published against `mongo-scala-bson` 5.6.5** (`org.mongodb:bson` 5.6.5).

The library uses only the BSON codec layer plus MongoDB's own `@BsonProperty` annotation. It needs
no part of `mongodb-driver-core`, `mongodb-driver-sync`, `mongodb-driver-reactivestreams` or
`mongo-scala-driver`, and imports nothing from `com.mongodb`.

Verified on Scala 3.7.4 — each of these compiles the library, passes the full suite, and produces
**byte-identical BSON**:

| Driver version | Status |
|---|---|
| 5.0.0 | minimum supported |
| 5.3.1 | tested |
| 5.6.5 | default |
| 5.9.2 | tested |
| 5.12.0 | newest validated |

**Minimum: 5.0.0.** The 4.x line is not supported — 4.11.5 was its last release and receives no
upstream fixes.

If your application depends on a newer driver in this range, your build's dependency resolution
selects it and the library works unchanged — no override or exclusion needed. This was verified
end-to-end, including against a live server, with the library compiled against 5.6.5 and running
against 5.12.0.

Versions between the tested ones are expected to work. **We do not claim "all 5.x versions are
supported"** — the five above are the tested ones.

*MongoDB **server** compatibility is a separate question that this project has not established a
matrix for. The integration suite runs against one pinned server version; it is not a support
statement.*

### If you use the native Scala 3 driver (`mongo-scala-driver_3`)

Since driver 5.7.0 MongoDB also publishes a native Scala 3 build. MongoScala3Codec currently
declares `mongo-scala-bson` as the Scala 2.13 artifact via `CrossVersion.for3Use2_13`, so putting
the two together makes **sbt fail the build**:

```
[error] Modules were resolved with conflicting cross-version suffixes:
[error]    org.mongodb.scala:mongo-scala-bson _2.13, _3
```

This is a packaging clash, not an incompatibility. The library's code is fine against the native
artifact: compiled against `mongo-scala-bson_3` 5.12.0, the whole suite passes unchanged. Exclude
the 2.13 artifact and let the native driver supply it:

```scala
libraryDependencies ++= Seq(
  ("io.github.mbannour" %% "mongoscala3codec" % "0.0.11")
    .exclude("org.mongodb.scala", "mongo-scala-bson_2.13"),
  "org.mongodb.scala" %% "mongo-scala-driver" % "5.12.0"   // native _3 build
)
```

`@BsonProperty` resolves from whichever of the two artifacts is present — the fully-qualified name
is the same in both — so your annotations keep working either way.

This combination is **not yet part of the tested matrix above**, and which artifact the library
should declare is an open question for 1.0. If you hit the error, this is why.


---

## Limitations

Known and deliberate, rather than hypothetical:

- **No typed filters or updates yet.** `MongoPath` gives you checked field *paths*; checking the
  value's type against the field's type is 1.1 work.
- **`Either` is not supported** and is not planned for 1.0. Use a sealed trait.
- **`scala.math.BigDecimal` and `scala.math.BigInt` have no codec.** Use `java.math.BigDecimal`.
  Both compile and fail on first encode.
- **`Array[T]` only works for `T = Byte`.**
- **Case objects cannot be sealed subtypes.** Use case classes or a Scala 3 `enum`.
- **No generic or type-parameterized models**, including sealed hierarchies with type parameters.
- **Models must have a stable path** — a case class declared inside a method is rejected at compile
  time. An `enum` declared inside a method-local object does not derive either.
- **Cyclic runtime object graphs are not supported**, and are not detected for you.
- **`@BsonIgnore` is lossy by design** — decoding restores the constructor default.
- **Ordinal enums are position-based**, with the evolution hazard described above.
- **An unsupported field type is a runtime failure, not a compile error**, when the type could in
  principle have come from the registry. This is the cost of letting any registry codec plug in.
- **Evolution inside a custom codec is that codec's business**, not something this library can
  promise anything about.
- **`CodecConfig.discriminatorStrategy` currently has no effect** — the discriminator value comes
  from the simple type name or `@BsonDiscriminator`. Do not rely on it.

---

## Roadmap

**1.0 — a codec foundation you can trust.** Derivation, the annotation set, the discriminator and
enum representations, recursive models, the supported-type contract, the compatibility matrices,
`CodecTestKit`, and the golden/property/evolution suites behind them. That is this README.

**1.1 — compile-time-checked filters and updates.** The direction the library exists for:

```scala
// PLANNED — NOT AVAILABLE IN 1.0. Illustrative syntax, not a committed API.
MongoFilter.gt[User](_.age, 18)        // should compile
MongoFilter.gt[User](_.email, 18)      // should NOT compile: email is a String
MongoUpdate.push[User](_.tags, "scala")
```

Everything would return the driver's `Bson`, so it stays interoperable with `Filters.*` and
`Updates.*` rather than replacing them.

**1.2 — more testing and schema-evolution tooling**, building on `CodecTestKit`.

**1.3 — typed sort, projection and index key specification.**

No dates. See [ROADMAP.md](ROADMAP.md) for the longer version. Nothing in the 1.1+ sections exists
today, and no API here was created to make a documentation example compile.

---

## Development

```bash
sbt test                          # unit tests on the default Scala version
sbt +test                         # the Scala cross-build
sbt ++3.8.0 test                  # one Scala version
sbt integrationTests/test         # integration tests (needs Docker; starts MongoDB in a container)
sbt mimaReportBinaryIssues        # binary compatibility against the published baseline
sbt -Dmongodb.version=5.12.0 test # the suite against a different MongoDB driver version
sbt scalafmtAll scalafmtSbt       # format sources and build files
sbt "benchmarks / Jmh / run"      # JMH benchmarks
```

### How 1.0 is verified

- **Golden BSON fixtures** — exact expected documents for every supported feature, so a macro
  change cannot alter the wire format quietly.
- **Property-based tests** — ScalaCheck over generated values, including the awkward ones: `Double`
  NaN, signed zero, `Int64` boundaries.
- **Schema-evolution fixtures** — the table above, executable.
- **Compile-negative tests** — every rejection asserted to *be* a compile error with a message that
  names the model and the fix.
- **A Scala compiler matrix** and **a MongoDB driver matrix**, both in CI.
- **MiMa** on every build.

No formal verification is claimed. These are tests.

---

## Contributing and license

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports and questions go to
[Issues](https://github.com/mbannour/MongoScala3Codec/issues) and
[Discussions](https://github.com/mbannour/MongoScala3Codec/discussions).

Further documentation lives in [docs/](docs/); release notes in [CHANGELOG.md](CHANGELOG.md).

Licensed under the Apache License 2.0 — see [LICENSE](LICENSE).
