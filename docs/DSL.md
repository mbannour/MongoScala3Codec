# Type-Safe Query/Update DSL

MongoScala3Codec ships a type-safe DSL for building MongoDB filters, updates, sorts, and projections
entirely at compile time — no strings, no reflection, no driver-specific imports required.

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [The `field` Function](#the-field-function)
  - [Nested Paths with Option](#nested-paths-with-option)
  - [Array Element Paths](#array-element-paths)
  - [@BsonProperty Support](#bsonproperty-support)
- [Filter Operators](#filter-operators)
  - [Equality and Comparison](#equality-and-comparison)
  - [Set Membership](#set-membership)
  - [Existence and Null Checks](#existence-and-null-checks)
  - [Regex](#regex)
  - [Array Filters](#array-filters)
- [Filter Combinators](#filter-combinators)
- [Update Operators](#update-operators)
  - [Field Updates](#field-updates)
  - [Numeric Operators](#numeric-operators)
  - [Array Mutations](#array-mutations)
  - [Combining Updates](#combining-updates)
- [Sort](#sort)
- [Projection](#projection)
- [BsonEncoder — Adding Custom Types](#bsonencoder--adding-custom-types)
- [Testing with DslTestKit](#testing-with-dsltestkit)
  - [assertFilter](#assertfilter)
  - [assertUpdate](#assertupdate)
  - [assertSort and assertProjection](#assertsort-and-assertprojection)
  - [Presence checks and inspection](#presence-checks-and-inspection)
  - [ScalaTest example](#scalatest-example)
- [Integration with RegistryBuilder](#integration-with-registrybuilder)
- [Complete Example](#complete-example)

---

## Installation

The DSL is a separate artifact so you only pay for what you use.

**Core codec + DSL:**

```scala
// build.sbt
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec"     % "0.0.11",
  "io.github.mbannour" %% "mongoscala3codec-dsl" % "0.0.11",
  "org.mongodb.scala" %% "mongo-scala-driver" % "5.7.0"  // native Scala 3 since 5.7
)
```

**DSL testing utilities** (add to `Test` scope):

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec-dsl-testkit" % "0.0.11" % Test
```

---

## Quick Start

```scala
import io.github.mbannour.mongo.dsl.*
import io.github.mbannour.fields.MongoPath.syntax.?   // Option field navigation

case class Address(city: String, @BsonProperty("zip") zipCode: Int)
case class User(_id: ObjectId, name: String, age: Int, active: Boolean, address: Option[Address])

// Filters
val filter = Filter.and(
  field[User](_.age) > 25,
  field[User](_.active) === true
)

// Updates
val update = Update.combine(
  field[User](_.name) := "Alice",
  field[User](_.age).inc(1)
)

// Sort
val sort = Sort.combine(field[User](_.age).desc, field[User](_.name).asc)

// Projection
val proj = Projection.combine(
  field[User](_.name).include,
  field[User](_.age).include,
  Projection.excludeId
)

// Use directly with the MongoDB Scala driver
collection.find(filter).sort(sort).projection(proj).toFuture()
collection.updateOne(filter, update).toFuture()
```

---

## The `field` Function

`field[Doc](selector)` is the entry point for every DSL expression. It takes a lambda that selects
a field on your document type and returns a `Field[Doc, A]` where `A` is the field's compile-time type.

```scala
val nameField = field[User](_.name)     // Field[User, String]
val ageField  = field[User](_.age)      // Field[User, Int]
val activeF   = field[User](_.active)   // Field[User, Boolean]
```

The field's BSON path is resolved at compile time via the same `MongoPath` macro used by the codec,
so there is no runtime cost and path typos become compilation errors.

### Nested Paths with Option

Import `MongoPath.syntax.?` to traverse `Option` fields without wrapping/unwrapping:

```scala
import io.github.mbannour.fields.MongoPath.syntax.?

case class Address(city: String)
case class User(name: String, address: Option[Address])

val cityField = field[User](_.address.?.city)   // Field[User, String], path = "address.city"
```

The `.?` hop is transparent — it does not add any path segment; it just lets the macro descend
into the `Option`'s type parameter.

### Array Element Paths

Import `MongoPath.syntax.each` to traverse array fields:

```scala
import io.github.mbannour.fields.MongoPath.syntax.each

case class Skill(name: String, level: Int)
case class Employee(name: String, skills: List[Skill])

val skillName = field[Employee](_.skills.each.name)   // path = "skills.name"
```

### @BsonProperty Support

`@BsonProperty` annotations on constructor parameters are automatically resolved:

```scala
case class Address(city: String, @BsonProperty("zip") zipCode: Int)
case class User(name: String, address: Option[Address])

field[User](_.address.?.zipCode).path   // "address.zip"
```

The same name translation that the codec uses for BSON documents is used here, so filters and
stored data always agree.

---

## Filter Operators

All filter operators return `org.bson.conversions.Bson` and are accepted directly by the MongoDB
Scala driver's `find`, `countDocuments`, `deleteOne`, `updateOne`, etc.

### Equality and Comparison

| Expression | MongoDB operator |
|------------|-----------------|
| `field[T](_.f) === value` | `{ f: { $eq: value } }` |
| `field[T](_.f) =/= value` | `{ f: { $ne: value } }` |
| `field[T](_.f) > value` | `{ f: { $gt: value } }` |
| `field[T](_.f) >= value` | `{ f: { $gte: value } }` |
| `field[T](_.f) < value` | `{ f: { $lt: value } }` |
| `field[T](_.f) <= value` | `{ f: { $lte: value } }` |

```scala
collection.find(field[User](_.age) >= 18).toFuture()
collection.find(field[User](_.name) =/= "deleted").toFuture()
```

### Set Membership

```scala
// Match documents where name is one of the given values
val filter = field[User](_.name).in("Alice", "Bob", "Carol")

// Exclude documents where status is one of the given values
val filter = field[User](_.status).nin("banned", "suspended")
```

### Existence and Null Checks

```scala
// Field exists in the document
field[User](_.email).exists()       // { email: { $exists: true } }
field[User](_.email).exists(false)  // { email: { $exists: false } }

// Field is null or missing
field[User](_.deletedAt).isNull     // { deletedAt: { $eq: null } }

// Field is not null
field[User](_.name).notNull         // { name: { $ne: null } }
```

### Regex

```scala
// Pattern match
field[User](_.name).regex("^Jo")

// Pattern with options (e.g. case-insensitive)
field[User](_.name).regex("alice", "i")
```

### Array Filters

```scala
// Array has exactly N elements
field[User](_.scores).hasSize(3)

// At least one array element matches a sub-filter
val inner = (field[User](_.age) > 5).asInstanceOf[BsonDocument]
field[User](_.scores).elemMatch(inner)
```

---

## Filter Combinators

`Filter` provides logical combinators that work on any `Bson` value, including expressions
produced by other DSL methods or the MongoDB driver's `Filters` object.

```scala
// AND — all conditions must be true
Filter.and(
  field[User](_.department) === "Engineering",
  field[User](_.age) >= 18,
  field[User](_.active) === true
)

// OR — at least one condition must be true
Filter.or(
  field[User](_.role) === "admin",
  field[User](_.salary) > 100_000.0
)

// NOR — none of the conditions may be true
Filter.nor(
  field[User](_.status) === "banned",
  field[User](_.status) === "deleted"
)

// NOT — negates a single condition
Filter.not(field[User](_.active) === false)

// Empty filter (matches all documents)
Filter.empty
```

`Filter.and` with a single argument returns the argument unchanged (no unnecessary wrapping).
`Filter.and` with no arguments returns an empty document.

---

## Update Operators

Update operators return `Bson` and are passed to `updateOne`, `updateMany`, `findOneAndUpdate`, etc.

### Field Updates

```scala
// $set — assign a new value
field[User](_.name) := "Alice"
field[User](_.active) := true

// $unset — remove a field from the document
field[User](_.deletedAt).unset

// $setOnInsert — set a value only when an upsert inserts a new document
field[User](_.createdAt).setOnInsert(Instant.now())

// $min / $max — update only if new value is lower / higher than current
field[User](_.priority).min(1)
field[User](_.priority).max(10)
```

### Numeric Operators

```scala
// $inc — add a delta (can be negative)
field[User](_.age).inc(1)
field[User](_.balance).inc(-50.0)

// $mul — multiply by a factor
field[User](_.salary).mul(1.1)   // 10% raise
```

### Array Mutations

These operators require that the field type `A` is a collection (`List`, `Seq`, `Set`, etc.).
The element type `E` is inferred automatically from the value you pass.

```scala
// $push — append an element
field[User](_.tags).push("scala")

// $pull — remove all matching elements
field[User](_.tags).pull("deprecated")

// $addToSet — add only if not already present
field[User](_.tags).addToSet("functional")
```

### Combining Updates

`Update.combine` merges multiple update expressions into a single `Bson` document, coalescing
operations under the same operator:

```scala
collection.updateOne(
  field[User](_._id) === userId,
  Update.combine(
    field[User](_.name)   := "Bob",          // $set: { name: "Bob", active: false }
    field[User](_.active) := false,
    field[User](_.age).inc(1),               // $inc: { age: 1 }
    field[User](_.tags).push("promoted")     // $push: { tags: "promoted" }
  )
).toFuture()
```

`Update.combine` with no arguments produces an empty document.

---

## Sort

```scala
// Single field — ascending / descending
collection.find(filter).sort(field[User](_.age).asc).toFuture()
collection.find(filter).sort(field[User](_.name).desc).toFuture()

// Multiple fields — first field has highest priority
val sort = Sort.combine(
  field[User](_.department).asc,
  field[User](_.salary).desc
)
collection.find(filter).sort(sort).toFuture()

// Convenience constructors using raw paths
Sort.ascending("age")
Sort.descending("salary")

// Text score sort (for $text queries)
Sort.byTextScore

// Natural order / reverse natural order
Sort.natural
Sort.naturalDesc
```

---

## Projection

```scala
// Include specific fields; exclude _id
val proj = Projection.combine(
  field[User](_.name).include,
  field[User](_.email).include,
  Projection.excludeId
)
collection.find(filter).projection(proj).toFuture()

// Exclude specific fields
val proj2 = Projection.combine(
  field[User](_.passwordHash).exclude,
  field[User](_.internalNotes).exclude
)

// Slice an array field
Projection.slice("scores", 5)           // first 5 elements
Projection.slice("scores", 2, 5)        // skip 2, take 5
```

`Projection.excludeId` — `{ _id: 0 }`
`Projection.includeId` — `{ _id: 1 }`

---

## BsonEncoder — Adding Custom Types

`BsonEncoder[A]` converts a Scala value to a `BsonValue`. Instances are summoned implicitly
wherever DSL operators accept a value.

**Built-in instances:**

| Scala type | BSON type |
|-----------|-----------|
| `String` | `BsonString` |
| `Int` | `BsonInt32` |
| `Long` | `BsonInt64` |
| `Double` | `BsonDouble` |
| `Float` | `BsonDouble` |
| `Boolean` | `BsonBoolean` |
| `Byte`, `Short` | `BsonInt32` |
| `ObjectId` | `BsonObjectId` |
| `BsonValue` | identity |
| `Option[A]` | `BsonNull` for `None`, delegates for `Some` |
| `List[A]`, `Seq[A]`, `Set[A]` | `BsonArray` |

**Adding your own:**

```scala
import io.github.mbannour.mongo.dsl.BsonEncoder
import org.bson.BsonString
import java.time.Instant

given BsonEncoder[Instant] with
  def encode(v: Instant) = new BsonString(v.toString)

// Now usable in any DSL expression
field[Event](_.occurredAt) === Instant.now()
field[Event](_.occurredAt) > Instant.parse("2025-01-01T00:00:00Z")
```

---

## Testing with DslTestKit

`mongoscala3codec-dsl-testkit` provides assertion helpers that let you verify the BSON
structure produced by DSL expressions — no MongoDB instance required.

```scala
// build.sbt
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec-dsl-testkit" % "0.0.11" % Test
```

```scala
import io.github.mbannour.mongo.dsl.testkit.DslTestKit
```

All helpers take a `Bson` value and throw `IllegalArgumentException` with a descriptive message
if the assertion fails, so they work with any test framework (ScalaTest, MUnit, Weaver, etc.).

### assertEqualsJson — compare against a MongoDB query string

The simplest way to pin down what a DSL expression produces is to compare its JSON output
against a MongoDB query string you write by hand.

```scala
// Equality filter
DslTestKit.assertEqualsJson(
  field[User](_.name) === "Alice",
  """{"name": {"$eq": "Alice"}}"""
)

// Range filter
DslTestKit.assertEqualsJson(
  field[User](_.age) > 18,
  """{"age": {"$gt": 18}}"""
)

// Compound filter
DslTestKit.assertEqualsJson(
  Filter.and(field[User](_.active) === true, field[User](_.age) >= 18),
  """{"$and": [{"active": {"$eq": true}}, {"age": {"$gte": 18}}]}"""
)

// Update
DslTestKit.assertEqualsJson(
  field[User](_.name) := "Bob",
  """{"$set": {"name": "Bob"}}"""
)

// Combined update
DslTestKit.assertEqualsJson(
  Update.combine(field[User](_.name) := "Carol", field[User](_.age).inc(1)),
  """{"$set": {"name": "Carol"}, "$inc": {"age": 1}}"""
)
```

### assertEquivalentTo — compare against MongoDB driver Filters/Updates/Sorts

`assertEquivalentTo` resolves both `Bson` values through the same BSON codec registry and
compares the resulting JSON. This lets you cross-check DSL output directly against the MongoDB
driver's official builder APIs.

```scala
import com.mongodb.client.model.{Filters, Updates, Sorts, Projections}

// Filters
DslTestKit.assertEquivalentTo(field[User](_.name)   === "Alice",  Filters.eq("name", "Alice"))
DslTestKit.assertEquivalentTo(field[User](_.name)   =/= "banned", Filters.ne("name", "banned"))
DslTestKit.assertEquivalentTo(field[User](_.age)    > 18,         Filters.gt("age", 18))
DslTestKit.assertEquivalentTo(field[User](_.age)    >= 21,        Filters.gte("age", 21))
DslTestKit.assertEquivalentTo(field[User](_.salary) < 50000.0,    Filters.lt("salary", 50000.0))
DslTestKit.assertEquivalentTo(field[User](_.salary) <= 100000.0,  Filters.lte("salary", 100000.0))
DslTestKit.assertEquivalentTo(field[User](_.name).in("Alice","Bob"), Filters.in("name","Alice","Bob"))

// Compound filters
DslTestKit.assertEquivalentTo(
  Filter.and(field[User](_.active) === true, field[User](_.age) >= 18),
  Filters.and(Filters.eq("active", true), Filters.gte("age", 18))
)

// Updates
DslTestKit.assertEquivalentTo(field[User](_.name) := "Bob",     Updates.set("name", "Bob"))
DslTestKit.assertEquivalentTo(field[User](_.name).unset,        Updates.unset("name"))
DslTestKit.assertEquivalentTo(field[User](_.age).inc(1),        Updates.inc("age", 1))
DslTestKit.assertEquivalentTo(field[User](_.tags).push("x"),    Updates.push("tags", "x"))
DslTestKit.assertEquivalentTo(field[User](_.tags).addToSet("x"),Updates.addToSet("tags", "x"))

// Sorts
DslTestKit.assertEquivalentTo(field[User](_.age).asc,  Sorts.ascending("age"))
DslTestKit.assertEquivalentTo(field[User](_.age).desc, Sorts.descending("age"))
DslTestKit.assertEquivalentTo(
  Sort.combine(field[User](_.age).desc, field[User](_.name).asc),
  Sorts.orderBy(Sorts.descending("age"), Sorts.ascending("name"))
)

// Projections
DslTestKit.assertEquivalentTo(field[User](_.name).include, Projections.include("name"))
DslTestKit.assertEquivalentTo(field[User](_.name).exclude, Projections.exclude("name"))
DslTestKit.assertEquivalentTo(Projection.excludeId,        Projections.excludeId())
DslTestKit.assertEquivalentTo(
  Projection.combine(field[User](_.name).include, field[User](_.age).include, Projection.excludeId),
  Projections.fields(Projections.include("name"), Projections.include("age"), Projections.excludeId())
)
```

> **Note:** `Filters.eq` in MongoDB driver 4.x+ produces `{ field: { $eq: value } }` — the same
> explicit operator form as the DSL. Older drivers using the shorthand `{ field: value }` would
> produce a different document and `assertEquivalentTo` would fail.

### assertFilter

Verifies that a filter has the shape `{ key: { operator: value } }`.

```scala
DslTestKit.assertFilter(field[User](_.name) === "Alice", "name", "$eq", new BsonString("Alice"))
DslTestKit.assertFilter(field[User](_.age) > 18,         "age",  "$gt", new BsonInt32(18))
```

### assertUpdate

Verifies that an update has the shape `{ operator: { key: value } }`.

```scala
DslTestKit.assertUpdate(field[User](_.name) := "Bob",   "$set", "name", new BsonString("Bob"))
DslTestKit.assertUpdate(field[User](_.age).inc(5),       "$inc", "age",  new BsonInt32(5))

// For Update.combine, each operator is checked separately
val combined = Update.combine(field[User](_.name) := "Carol", field[User](_.age).inc(1))
DslTestKit.assertUpdate(combined, "$set", "name", new BsonString("Carol"))
DslTestKit.assertUpdate(combined, "$inc", "age",  new BsonInt32(1))
```

### assertSort and assertProjection

```scala
DslTestKit.assertSort(field[User](_.age).desc, key = "age", ascending = false)
DslTestKit.assertSort(Sort.combine(field[User](_.age).desc, field[User](_.name).asc), "name", ascending = true)

DslTestKit.assertProjection(Projection.combine(field[User](_.name).include, Projection.excludeId), "name", include = true)
DslTestKit.assertProjection(Projection.excludeId, "_id", include = false)
```

### Presence checks and inspection

```scala
DslTestKit.assertHasKey(filter, "name")
DslTestKit.assertMissingKey(filter, "age")

// Render any Bson to a JSON string (handles non-BsonDocument Bson too)
val json: String = DslTestKit.render(filter)

// Also available as toJson (alias)
val json2: String = DslTestKit.toJson(filter)

// Extract top-level entries as a Map
val m: Map[String, BsonValue] = DslTestKit.toMap(filter)

// Drill into a nested sub-document
val inner: BsonDocument = DslTestKit.innerDoc(filter, "name")
```

### ScalaTest example

```scala
import io.github.mbannour.mongo.dsl.*
import io.github.mbannour.mongo.dsl.testkit.DslTestKit
import org.bson.{BsonBoolean, BsonInt32, BsonString}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UserQuerySpec extends AnyFunSuite with Matchers:

  case class User(_id: org.bson.types.ObjectId, name: String, age: Int, active: Boolean, tags: List[String])

  test("active adult filter encodes correctly") {
    val filter = Filter.and(
      field[User](_.active) === true,
      field[User](_.age) >= 18
    )
    // Inspect structure via DslTestKit
    DslTestKit.assertFilter(
      DslTestKit.innerDoc(filter, "$and").getArray("$and").get(0).asDocument(),
      "active", "$eq", new BsonBoolean(true)
    )
    // Or check the combined JSON for quick smoke-test
    assert(DslTestKit.toJson(filter).contains("$and"))
  }

  test("profile update sets name and increments age") {
    val update = Update.combine(
      field[User](_.name) := "Alice",
      field[User](_.age).inc(1)
    )
    DslTestKit.assertUpdate(update, "$set", "name", new BsonString("Alice"))
    DslTestKit.assertUpdate(update, "$inc", "age",  new BsonInt32(1))
  }

  test("push encodes to $push operator") {
    val pushUpdate = field[User](_.tags).push("promoted")
    DslTestKit.assertUpdate(pushUpdate, "$push", "tags", new BsonString("promoted"))
  }

  test("sort is age desc then name asc") {
    val sort = Sort.combine(field[User](_.age).desc, field[User](_.name).asc)
    DslTestKit.assertSort(sort, "age",  ascending = false)
    DslTestKit.assertSort(sort, "name", ascending = true)
  }

  test("projection includes name and excludes _id") {
    val proj = Projection.combine(field[User](_.name).include, Projection.excludeId)
    DslTestKit.assertProjection(proj, "name", include = true)
    DslTestKit.assertProjection(proj, "_id",  include = false)
    DslTestKit.assertMissingKey(proj, "age")
  }
```

---

## Integration with RegistryBuilder

The DSL is independent of the codec — it only needs `mongo-scala-bson`, which is a transitive
dependency of the library. No extra configuration is required. Use `RegistryBuilder` for encoding
and decoding, and the DSL for building query expressions:

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder
import io.github.mbannour.mongo.dsl.*
import org.mongodb.scala.*

case class Product(_id: ObjectId, name: String, price: Double, inStock: Boolean)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .register[Product]
  .build

val db  = MongoClient("mongodb://localhost:27017")
          .getDatabase("store").withCodecRegistry(registry)
val col = db.getCollection[Product]("products")

// Type-safe filter + update
col.updateMany(
  Filter.and(
    field[Product](_.inStock) === true,
    field[Product](_.price) > 1000.0
  ),
  Update.combine(
    field[Product](_.price).mul(0.9),   // 10% discount
    field[Product](_.name) := "SALE - " + "item"
  )
).toFuture()
```

---

## Complete Example

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder
import io.github.mbannour.mongo.dsl.*
import io.github.mbannour.fields.MongoPath.syntax.?
import org.bson.types.ObjectId
import org.mongodb.scala.*
import org.mongodb.scala.bson.annotations.BsonProperty
import scala.concurrent.ExecutionContext.Implicits.global

case class Address(city: String, @BsonProperty("zip") zipCode: Int)

case class Employee(
  _id: ObjectId,
  name: String,
  department: String,
  salary: Double,
  active: Boolean,
  address: Option[Address],
  skills: List[String]
)

@main def run(): Unit =
  val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .ignoreNone
    .registerAll[(Address, Employee)]
    .build

  val client = MongoClient("mongodb://localhost:27017")
  val col    = client
    .getDatabase("hr").withCodecRegistry(registry)
    .getCollection[Employee]("employees")

  // ── Filter: active engineers in Paris earning above 80k ────────────────
  val filter = Filter.and(
    field[Employee](_.department)      === "Engineering",
    field[Employee](_.salary)          >   80_000.0,
    field[Employee](_.active)          === true,
    field[Employee](_.address.?.city)  === "Paris"
  )

  // ── Update: give them a raise and add a skill ──────────────────────────
  val update = Update.combine(
    field[Employee](_.salary).mul(1.05),
    field[Employee](_.skills).addToSet("Scala 3")
  )

  // ── Sort: by salary descending, then name ascending ────────────────────
  val sort = Sort.combine(
    field[Employee](_.salary).desc,
    field[Employee](_.name).asc
  )

  // ── Projection: name, salary, skills only ─────────────────────────────
  val proj = Projection.combine(
    field[Employee](_.name).include,
    field[Employee](_.salary).include,
    field[Employee](_.skills).include,
    Projection.excludeId
  )

  // Execute
  for
    _       <- col.updateMany(filter, update).toFuture()
    results <- col.find(filter).sort(sort).projection(proj).toFuture()
  do
    results.foreach(e => println(s"${e.name}: ${e.salary}"))

  client.close()
```
