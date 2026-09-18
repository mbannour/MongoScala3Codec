# BSON type mapping

What each Scala type becomes in the stored document.

Every mapping on this page is asserted in the test suite against a fixed expected document. The
representations of the **supported** types are frozen for the 1.x line: see
[Wire compatibility](../README.md#bson-wire-compatibility).

Three groups matter, and the difference between them is the difference between "this is our
contract" and "this happens to work":

- **Derived** — this library writes the value itself. The representation is ours, and it is frozen.
- **Delegated** — this library hands the field to whatever `Codec` your registry holds for that
  class. It works, the shape belongs to that codec, and we do not freeze it.
- **No codec** — nothing in the registry covers the type. It **compiles** and then throws on the
  first encode.

That last group exists because derivation does not reject unknown field types: it defers to the
registry, which is exactly how MongoDB's own codecs and yours plug in. The price is that an
unsupported field type is a runtime failure rather than a compile error.

---

## Derived — frozen representations

| Scala type | BSON type | Example document |
|---|---|---|
| `String` | String | `{"s": "s"}` |
| `Boolean` | Boolean | `{"b": true}` |
| `Int` | Int32 | `{"i": 1}` |
| `Short` | Int32 | `{"sh": 6}` |
| `Byte` | Int32 | `{"by": 5}` |
| `Long` | Int64 | `{"l": {"$numberLong": "2"}}` |
| `Double` | Double | `{"d": 3.5}` |
| `Float` | Double | `{"f": 4.5}` |
| `Char` | **Int32 of the code point** | `{"ch": 120}` for `'x'` |
| `java.util.UUID` | **String** | `{"uuid": "00000000-0000-0000-0000-000000000001"}` |
| `org.bson.types.ObjectId` | ObjectId | `{"oid": {"$oid": "507f1f77bcf86cd799439011"}}` |
| `Option[T]` | `T`, or null / absent | see [Option](#option) |
| `List[T]`, `Seq[T]`, `Vector[T]`, `Set[T]` | Array | `{"list": ["a", "b"]}` |
| `Map[String, V]` | embedded document | `{"m": {"k": "v"}}` |
| case class | embedded document | `{"inner": {"city": "Paris", "zip": 75001}}` |
| sealed trait / class | document with a discriminator | `{"_type": "Dog", "name": "Rex"}` |
| Scala 3 `enum` | String or Int32 | see [Enums](#enums) |
| self-recursive case class | nested documents | `{"value": "a", "next": {"value": "b", "next": null}}` |

Two of these surprise people, so they are called out:

- **`Char` is an Int32**, not a one-character string. `'x'` is stored as `120`.
- **`UUID` is a String**, not Binary. Derivation writes the UUID itself rather than deferring to
  the driver's `UuidCodec`, which would write Binary subtype 4. If you need the Binary form, supply
  your own `Codec[UUID]` — but be aware that changes the stored shape of existing documents.

### Numeric types

```scala
case class Scalars(i: Int, l: Long, d: Double, f: Float, b: Boolean, by: Byte, sh: Short, ch: Char, s: String)
```

```json
{"i": 1, "l": {"$numberLong": "2"}, "d": 3.5, "f": 4.5, "b": true,
 "by": 5, "sh": 6, "ch": 120, "s": "s"}
```

`Int`, `Short` and `Byte` all land on Int32; `Long` on Int64; `Float` widens to Double. Reading is
slightly more forgiving than writing: a stored Int32 decodes into a `Long` field, and a stored
Int64 decodes into an `Int` field **if the value fits** — if it does not, decoding fails rather
than truncating.

`Double` keeps the awkward values: NaN, both infinities and negative zero all survive a round trip.

### Option

`Some(v)` writes `v`. What `None` writes is chosen on the builder:

```scala
RegistryBuilder.from(base).encodeNone.register[User].build   // default
RegistryBuilder.from(base).ignoreNone.register[User].build
```

| Setting | `None` on the wire |
|---|---|
| `encodeNone` (default) | `{"name": "n", "nickname": null}` |
| `ignoreNone` | `{"name": "n"}` |

Decoding accepts **both**: a missing field and a stored `null` both become `None`. Changing this
setting is therefore safe for readers and changes only new writes.

> Configuration goes on the builder. A `given CodecConfig` in scope is **not** summoned by
> `RegistryBuilder` — pass it explicitly with `withConfig(summon[CodecConfig])` if you keep one.

### Collections

```scala
case class Collections(list: List[String], seq: Seq[Int], vector: Vector[String], set: Set[Int])
```

```json
{"list": ["a", "b"], "seq": [1, 2], "vector": ["v"], "set": [3]}
```

Empty collections are empty arrays, not null or absent:

```json
{"list": [], "seq": [], "vector": [], "set": []}
```

`Set` is stored as an array, so element order is not meaningful on the way back.

### Maps

`Map[String, V]` becomes an embedded document:

```scala
case class Maps(strings: Map[String, String], ints: Map[String, Int])
```

```json
{"strings": {"k": "v"}, "ints": {"n": 1}}
```

**Non-`String` keys are a compile error**, naming the model and the requirement. There is no
array-of-pairs fallback.

### Enums

Via `EnumValueCodecProvider`, in one of two modes:

```scala
enum Colour:
  case Red, Green, Blue

case class Paint(name: String, colour: Colour)
```

| Provider | BSON | Document |
|---|---|---|
| `forStringEnum[Colour]` | String | `{"name": "wall", "colour": "Green"}` |
| `forOrdinalEnum[Colour]` | Int32 | `{"name": "wall", "colour": 2}` |

Enums nested in an `object` encode identically to top-level ones.

**Ordinal mode stores a position.** Inserting or reordering cases silently changes what historical
documents mean — see the warning in [the README](../README.md#ordinal-enums-and-the-order-of-your-cases).

### Discriminators

```json
{"_type": "Dog", "name": "Rex", "breed": "Labrador"}
```

The field is `_type` by default and is written **first**; the value is the simple type name unless
`@BsonDiscriminator` overrides it. Full detail in
[SEALED_TRAIT_SUPPORT.md](SEALED_TRAIT_SUPPORT.md).

---

## Delegated — your registry decides

These are not written by derivation. They reach a codec the registry already holds, that codec
decides the shape, and this library does not freeze it.

| Scala type | BSON type | Provided by | Example |
|---|---|---|---|
| `Array[Byte]` | Binary, subtype 0 | the driver's byte-array codec | `{"data": {"$binary": {"base64": "AQID", "subType": "00"}}}` |
| `java.math.BigDecimal` | Decimal128 | the driver's decimal codec | `{"amount": {"$numberDecimal": "12.34"}}` |
| `java.util.Date` | UTC datetime | `ValueCodecProvider` | `{"date": {"$date": "1970-01-01T00:00:00Z"}}` |
| `java.time.Instant` | UTC datetime | `Jsr310CodecProvider` | `{"instant": {"$date": "1970-01-01T00:00:00Z"}}` |
| `java.time.LocalDate` | UTC datetime | `Jsr310CodecProvider` | `{"localDate": {"$date": "2026-01-01T00:00:00Z"}}` |
| `java.time.LocalDateTime` | UTC datetime | `Jsr310CodecProvider` | `{"localDateTime": {"$date": "2026-01-01T00:00:00Z"}}` |
| opaque type alias | as the underlying type | the underlying type's codec | `opaque type UserId = String` → `{"userId": "u-1"}` |
| any type you have a `Codec` for | whatever your codec writes | your codec | see below |

The JSR-310 provider has to actually be in your base registry.
`MongoClient.DEFAULT_CODEC_REGISTRY` includes it; a registry you assemble by hand from individual
providers may not.

### Your own codec wins, even over a case class

```scala
final case class Money(cents: Long)

class MoneyCodec extends Codec[Money]:
  def encode(writer: BsonWriter, value: Money, ctx: EncoderContext): Unit = writer.writeInt64(value.cents)
  def decode(reader: BsonReader, ctx: DecoderContext): Money = Money(reader.readInt64())
  def getEncoderClass: Class[Money] = classOf[Money]

case class Priced(name: String, price: Money)

RegistryBuilder.from(base).withCodec(new MoneyCodec()).register[Priced].build
```

`Money` is a case class and is still stored as the Int64 its own codec writes:

```json
{"name": "widget", "price": {"$numberLong": "500"}}
```

Whatever happens inside your codec — including how it copes with change — is yours. This library
cannot promise anything about it.

---

## No codec — compiles, then throws

| Scala type | What you get |
|---|---|
| `scala.math.BigDecimal` | `IllegalArgumentException: No codec found for type: scala.math.BigDecimal` — use `java.math.BigDecimal` |
| `scala.math.BigInt` | `IllegalArgumentException: No codec found for type: scala.math.BigInt` |
| `java.time.ZonedDateTime` | `IllegalArgumentException: No codec found for type: java.time.ZonedDateTime` — the driver's JSR-310 provider does not cover it; supply your own `Codec[ZonedDateTime]` |
| `Array[T]`, `T` ≠ `Byte` | `IllegalArgumentException: No codec found` |
| `Either[L, R]` | throws, naming `Either` — **not part of the v1.0 contract**; model the choice as a sealed trait |

These are characterized in the test suite so a change in behaviour shows up as a failing test. They
are recorded, not endorsed.

---

## Rejected at compile time

| Shape | The diagnostic names |
|---|---|
| `Map` with non-`String` keys | the model, and that keys must be `String` |
| a tuple field | the model, the field and the type |
| a field type that can neither be derived nor delegated | the model, the field and the type |
| a model declared inside a method (unstable path) | the model, and why |
| two `@BsonId` fields | the conflict |
| `@BsonId` and `@BsonIgnore` on one field | the conflict |
| `@BsonIgnore` on a field with no constructor default | the field |
| duplicate `@BsonDiscriminator` values | the subtypes |
| an empty `@BsonDiscriminator` value | the subtype |

---

## Not supported

- `scala.util.Try[T]` — use a sealed trait.
- `Either[L, R]` — not in the v1.0 contract.
- Case objects as sealed subtypes — use case classes or a Scala 3 `enum`.
- Generic / type-parameterised case classes and sealed hierarchies.
- Cyclic runtime object graphs. BSON documents are trees; a cycle has no finite document, and
  nothing detects it for you.

---

## See also

- [../README.md](../README.md) — the supported-type contract and the compatibility matrices
- [../README.md#schema-evolution](../README.md#schema-evolution) — what happens to stored documents
  when the model changes
- [ENUM_SUPPORT.md](ENUM_SUPPORT.md) — enums in depth
- [SEALED_TRAIT_SUPPORT.md](SEALED_TRAIT_SUPPORT.md) — discriminators in depth
