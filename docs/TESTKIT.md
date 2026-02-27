# CodecTestKit Guide

Use `mongoscala3codec-testkit` to test codec behavior without a running MongoDB instance.

It is published as a separate artifact so production apps can keep test-only utilities out of runtime dependencies.

## Add Dependency

Use the same version as `mongoscala3codec`.

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.10",
  "io.github.mbannour" %% "mongoscala3codec-testkit" % "0.0.10" % Test
)
```

## Basic Usage

```scala
import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import org.bson.codecs.Codec
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient

case class User(_id: ObjectId, name: String, email: Option[String])

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build

given Codec[User] = registry.get(classOf[User])

// 1) Round-trip assertion
CodecTestKit.assertCodecSymmetry(User(new ObjectId(), "Alice", Some("alice@example.com")))

// 2) BSON inspection
val bson = CodecTestKit.toBsonDocument(User(new ObjectId(), "Bob", None))
assert(bson.getString("name").getValue == "Bob")
```

## Example Test Implementation (ScalaTest)

```scala
import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import org.bson.codecs.Codec
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

case class User(_id: ObjectId, name: String, email: Option[String])

class UserCodecSpec extends AnyFlatSpec with Matchers:

  private val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .ignoreNone
    .register[User]
    .build

  given Codec[User] = registry.get(classOf[User])

  "User codec" should "round-trip values correctly" in {
    val user = User(new ObjectId("507f1f77bcf86cd799439011"), "Alice", Some("alice@example.com"))
    CodecTestKit.roundTrip(user) shouldBe user
  }

  it should "omit None fields with ignoreNone policy" in {
    val user = User(new ObjectId("507f1f77bcf86cd799439012"), "Bob", None)
    val bson = CodecTestKit.toBsonDocument(user)

    bson.containsKey("name") shouldBe true
    bson.containsKey("email") shouldBe false
  }

  it should "produce expected BSON fragments" in {
    val user = User(new ObjectId("507f1f77bcf86cd799439013"), "Carol", Some("carol@example.com"))
    CodecTestKit.assertBsonContains(
      user,
      Map("name" -> new org.bson.BsonString("Carol"))
    )
  }
```

## Best Practices

1. Keep `mongoscala3codec-testkit` in `% Test` scope only.
2. Use the same version for `mongoscala3codec` and `mongoscala3codec-testkit`.
3. Build one registry per test suite and register only the types needed for that suite.
4. Test both round-trip behavior (`assertCodecSymmetry`/`roundTrip`) and BSON structure (`toBsonDocument`, `assertBsonContains`).
5. Cover both `Some` and `None` cases when your model has optional fields.
6. Use fixed values in tests (`ObjectId`, timestamps, strings) to keep tests deterministic.
7. Keep unit tests with `CodecTestKit` separate from integration tests that hit real MongoDB.
8. When debugging failures, print `CodecTestKit.diff(...)` or `CodecTestKit.deepDiff(...)` output for faster diagnosis.

## Efficient Coverage Strategy

Use this test pyramid for each important codec/model:

1. Round-trip symmetry tests for representative samples.
2. BSON shape assertions for field names and critical values.
3. Edge-case matrix: `Some`/`None`, defaults, empty collections, unicode, min/max numerics.
4. Negative decode tests: missing required field, wrong BSON type, unknown discriminator for sealed traits.
5. Property-based round-trip checks for broad input coverage.
6. Small integration layer with real MongoDB only for driver/interoperability behavior.

## Comprehensive Unit Test Template

This template covers all layers above in one suite.

```scala
import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import org.bson.codecs.Codec
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonInt32, BsonObjectId, BsonString}
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.*

case class Address(city: String, @BsonProperty("zip") zipCode: Int)
case class User(
    _id: ObjectId,
    name: String,
    email: Option[String],
    tags: List[String] = Nil,
    address: Address
)

class UserCodecComprehensiveSpec extends AnyFlatSpec with Matchers:

  private val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .ignoreNone
    .registerAll[(Address, User)]
    .build

  given Codec[User] = registry.get(classOf[User])

  "User codec" should "round-trip representative values" in {
    val sample = User(
      _id = new ObjectId("507f1f77bcf86cd799439011"),
      name = "Alice",
      email = Some("alice@example.com"),
      tags = List("admin", "owner"),
      address = Address("Paris", 75001)
    )

    CodecTestKit.roundTrip(sample) shouldBe sample
  }

  it should "encode expected BSON names/values" in {
    val sample = User(
      _id = new ObjectId("507f1f77bcf86cd799439012"),
      name = "Bob",
      email = None,
      address = Address("Berlin", 10115)
    )

    CodecTestKit.assertBsonContains(
      sample,
      Map(
        "name" -> new BsonString("Bob")
      )
    )

    val bson = CodecTestKit.toBsonDocument(sample)
    bson.containsKey("email") shouldBe false          // ignoreNone
    bson.getDocument("address").containsKey("zip") shouldBe true // @BsonProperty
  }

  it should "cover edge values" in {
    val edge = User(
      _id = new ObjectId("507f1f77bcf86cd799439013"),
      name = "Ω-ユーザー",
      email = Some(""),
      tags = Nil,
      address = Address("", Int.MaxValue)
    )
    CodecTestKit.assertCodecSymmetry(edge)
  }

  it should "fail decode when required fields are missing" in {
    val missingRequired = new BsonDocument()
      .append("_id", new BsonObjectId(new ObjectId("507f1f77bcf86cd799439014")))
      .append("address", new BsonDocument().append("city", new BsonString("Rome")).append("zip", new BsonInt32(1)))
    an[Exception] should be thrownBy CodecTestKit.fromBsonDocument[User](missingRequired)
  }

  it should "fail decode when BSON field types are invalid" in {
    val wrongType = new BsonDocument()
      .append("_id", new BsonObjectId(new ObjectId("507f1f77bcf86cd799439015")))
      .append("name", new BsonInt32(123)) // should be string
      .append("address", new BsonDocument().append("city", new BsonString("Rome")).append("zip", new BsonInt32(1)))
    an[Exception] should be thrownBy CodecTestKit.fromBsonDocument[User](wrongType)
  }

  private val genUser: Gen[User] =
    for
      name <- Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
      email <- Gen.option(Gen.alphaNumStr.map(s => s"$s@example.com"))
      tags <- Gen.listOf(Gen.alphaStr)
      city <- Gen.alphaStr
      zip <- Gen.chooseNum(1, 99999)
    yield User(new ObjectId(), name, email, tags, Address(city, zip))

  it should "satisfy property-based round-trip symmetry" in {
    forAll(genUser) { value =>
      CodecTestKit.roundTrip(value) shouldBe value
    }
  }
```

If your model uses sealed traits, add one negative test that decodes an unknown discriminator value and assert failure.

## Useful Helpers

- `roundTrip(value)` - encode/decode and return the decoded value
- `assertCodecSymmetry(value)` - assert round-trip correctness
- `toBsonDocument(value)` - inspect encoded BSON
- `fromBsonDocument[T](doc)` - decode raw BSON
- `assertBsonStructure(value, expected)` - strict BSON structure assertion
- `assertBsonContains(value, expectedFields)` - partial BSON assertion
- `diff(expected, actual)` / `deepDiff(expected, actual)` - readable failure diagnostics

## Framework Compatibility

`CodecTestKit` is framework-agnostic. Use it with ScalaTest, MUnit, uTest, or plain assertions.
