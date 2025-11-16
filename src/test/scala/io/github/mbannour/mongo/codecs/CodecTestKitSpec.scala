package io.github.mbannour.mongo.codecs

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{Codec, *}
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonInt32, BsonObjectId, BsonString}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** Unit tests for CodecTestKit without requiring MongoDB instance. These tests demonstrate all CodecTestKit features for testing BSON
  * codecs.
  */
class CodecTestKitSpec extends AnyFlatSpec with Matchers:

  // Default BSON codec registry with standard codecs
  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new StringCodec(),
    new IntegerCodec(),
    new LongCodec(),
    new DoubleCodec(),
    new BooleanCodec(),
    new ObjectIdCodec()
  )

  // Test models
  case class User(_id: ObjectId, name: String, age: Int)

  case class Account(_id: ObjectId, username: String, balance: Double, active: Boolean)

  case class UserProfile(_id: ObjectId, name: String, email: Option[String], bio: Option[String])

  case class ShoppingCart(_id: ObjectId, items: List[String], total: Double)

  case class Tags(_id: ObjectId, tags: Set[String])

  // Models for nested case class test
  case class Address(_id: ObjectId, street: String, city: String)
  case class Contact(_id: ObjectId, email: String, phone: Option[String])
  case class Person(_id: ObjectId, name: String, address: Option[Address], contact: Contact)

  "CodecTestKit.roundTrip" should "encode and decode values correctly" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "Alice", 30)
    val result = CodecTestKit.roundTrip(user)

    result shouldBe user
    result.name shouldBe "Alice"
    result.age shouldBe 30
  }

  "CodecTestKit.assertCodecSymmetry" should "pass for valid codecs" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "Bob", 25)

    // Should not throw an exception
    noException should be thrownBy {
      CodecTestKit.assertCodecSymmetry(user)
    }
  }

  it should "work with multiple data types" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Account]
      .build

    given codec: Codec[Account] = registry.get(classOf[Account])

    val account = Account(new ObjectId(), "user123", 1000.50, true)
    CodecTestKit.assertCodecSymmetry(account)
  }

  "CodecTestKit.toBsonDocument" should "convert case class to BsonDocument" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "Charlie", 35)
    val bsonDoc = CodecTestKit.toBsonDocument(user)

    bsonDoc shouldBe a[BsonDocument]
    bsonDoc.containsKey("_id") shouldBe true
    bsonDoc.getString("name").getValue shouldBe "Charlie"
    bsonDoc.getInt32("age").getValue shouldBe 35
  }

  it should "preserve all field types correctly" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Account]
      .build

    given codec: Codec[Account] = registry.get(classOf[Account])

    val account = Account(new ObjectId(), "dave", 999.99, false)
    val bson = CodecTestKit.toBsonDocument(account)

    bson.getString("username").getValue shouldBe "dave"
    bson.getDouble("balance").getValue shouldBe 999.99
    bson.getBoolean("active").getValue shouldBe false
  }

  "CodecTestKit.fromBsonDocument" should "decode BsonDocument to case class" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val objectId = new ObjectId()
    val bsonDoc = new BsonDocument()
      .append("_id", new BsonObjectId(objectId))
      .append("name", new BsonString("Eve"))
      .append("age", new BsonInt32(28))

    val user = CodecTestKit.fromBsonDocument[User](bsonDoc)

    user._id shouldBe objectId
    user.name shouldBe "Eve"
    user.age shouldBe 28
  }

  "CodecTestKit.assertBsonStructure" should "validate BSON structure" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "Frank", 40)
    val expectedBson = new BsonDocument()
      .append("_id", new BsonObjectId(user._id))
      .append("name", new BsonString("Frank"))
      .append("age", new BsonInt32(40))

    // Should not throw exception
    noException should be thrownBy {
      CodecTestKit.assertBsonStructure(user, expectedBson)
    }
  }

  "CodecTestKit with NoneHandling.Encode" should "encode None as null" in {
    val config = CodecConfig(noneHandling = NoneHandling.Encode)

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .withConfig(config)
      .register[UserProfile]
      .build

    given codec: Codec[UserProfile] = registry.get(classOf[UserProfile])

    val profile = UserProfile(new ObjectId(), "Grace", None, None)
    val bson = CodecTestKit.toBsonDocument(profile)

    // Fields should be present as null
    bson.containsKey("email") shouldBe true
    bson.get("email").isNull shouldBe true
    bson.containsKey("bio") shouldBe true
    bson.get("bio").isNull shouldBe true

    // Verify round-trip still works
    CodecTestKit.assertCodecSymmetry(profile)
  }

  it should "encode Some values correctly" in {
    val config = CodecConfig(noneHandling = NoneHandling.Encode)

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .withConfig(config)
      .register[UserProfile]
      .build

    given codec: Codec[UserProfile] = registry.get(classOf[UserProfile])

    val profile = UserProfile(
      new ObjectId(),
      "Helen",
      Some("helen@example.com"),
      Some("Developer")
    )
    val bson = CodecTestKit.toBsonDocument(profile)

    bson.getString("email").getValue shouldBe "helen@example.com"
    bson.getString("bio").getValue shouldBe "Developer"

    CodecTestKit.assertCodecSymmetry(profile)
  }

  "CodecTestKit with NoneHandling.Ignore" should "omit None fields" in {
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .withConfig(config)
      .register[UserProfile]
      .build

    given codec: Codec[UserProfile] = registry.get(classOf[UserProfile])

    val profile = UserProfile(new ObjectId(), "Ivy", None, None)
    val bson = CodecTestKit.toBsonDocument(profile)

    // Fields should be omitted
    bson.containsKey("email") shouldBe false
    bson.containsKey("bio") shouldBe false

    // But required fields should still be present
    bson.containsKey("_id") shouldBe true
    bson.containsKey("name") shouldBe true

    CodecTestKit.assertCodecSymmetry(profile)
  }

  it should "include Some values even with Ignore policy" in {
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .withConfig(config)
      .register[UserProfile]
      .build

    given codec: Codec[UserProfile] = registry.get(classOf[UserProfile])

    val profile = UserProfile(new ObjectId(), "Jack", Some("jack@test.com"), None)
    val bson = CodecTestKit.toBsonDocument(profile)

    // email should be present, bio should be omitted
    bson.containsKey("email") shouldBe true
    bson.getString("email").getValue shouldBe "jack@test.com"
    bson.containsKey("bio") shouldBe false

    CodecTestKit.assertCodecSymmetry(profile)
  }

  "CodecTestKit with collections" should "handle List fields" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[ShoppingCart]
      .build

    given codec: Codec[ShoppingCart] = registry.get(classOf[ShoppingCart])

    val cart = ShoppingCart(new ObjectId(), List("apple", "banana", "cherry"), 15.99)
    val bson = CodecTestKit.toBsonDocument(cart)

    bson.getArray("items").size() shouldBe 3
    bson.getDouble("total").getValue shouldBe 15.99

    CodecTestKit.assertCodecSymmetry(cart)
  }

  it should "handle Set fields" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Tags]
      .build

    given codec: Codec[Tags] = registry.get(classOf[Tags])

    val tags = Tags(new ObjectId(), Set("scala", "mongodb", "testing"))
    val bson = CodecTestKit.toBsonDocument(tags)

    bson.getArray("tags").size() shouldBe 3

    CodecTestKit.assertCodecSymmetry(tags)
  }

  it should "handle empty collections" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[ShoppingCart]
      .register[Tags]
      .build

    given cartCodec: Codec[ShoppingCart] = registry.get(classOf[ShoppingCart])
    given tagsCodec: Codec[Tags] = registry.get(classOf[Tags])

    val emptyCart = ShoppingCart(new ObjectId(), List.empty, 0.0)
    val emptyTags = Tags(new ObjectId(), Set.empty)

    CodecTestKit.assertCodecSymmetry(emptyCart)
    CodecTestKit.assertCodecSymmetry(emptyTags)

    val cartBson = CodecTestKit.toBsonDocument(emptyCart)
    val tagsBson = CodecTestKit.toBsonDocument(emptyTags)

    cartBson.getArray("items").size() shouldBe 0
    tagsBson.getArray("tags").size() shouldBe 0
  }

  "CodecTestKit.testRegistry" should "create minimal registry" in {

    val fullRegistry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .register[Account]
      .build

    val userCodec = fullRegistry.get(classOf[User])
    val accountCodec = fullRegistry.get(classOf[Account])

    val testReg = CodecTestKit.testRegistry(userCodec, accountCodec)

    // Codecs should be available in test registry
    testReg.get(classOf[User]) should not be null
    testReg.get(classOf[Account]) should not be null
  }

  "CodecTestKit with edge cases" should "handle empty strings" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "", 0)
    CodecTestKit.assertCodecSymmetry(user)

    val bson = CodecTestKit.toBsonDocument(user)
    bson.getString("name").getValue shouldBe ""
  }

  it should "handle special characters" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "User-123_@!#$%", 42)
    CodecTestKit.assertCodecSymmetry(user)
  }

  it should "handle Unicode characters" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "用户名 👤 Пользователь", 30)
    CodecTestKit.assertCodecSymmetry(user)

    val bson = CodecTestKit.toBsonDocument(user)
    bson.getString("name").getValue shouldBe "用户名 👤 Пользователь"
  }

  it should "handle boundary values" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val maxUser = User(new ObjectId(), "Max", Int.MaxValue)
    val minUser = User(new ObjectId(), "Min", Int.MinValue)

    CodecTestKit.assertCodecSymmetry(maxUser)
    CodecTestKit.assertCodecSymmetry(minUser)
  }

  "CodecTestKit with nested case classes" should "handle complex nested structures" in {

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Address]
      .register[Contact]
      .register[Person]
      .build

    given addressCodec: Codec[Address] = registry.get(classOf[Address])
    given contactCodec: Codec[Contact] = registry.get(classOf[Contact])
    given personCodec: Codec[Person] = registry.get(classOf[Person])

    val address = Address(new ObjectId(), "123 Main St", "Springfield")
    val contact = Contact(new ObjectId(), "test@example.com", Some("555-1234"))
    val person = Person(new ObjectId(), "John Doe", Some(address), contact)

    CodecTestKit.assertCodecSymmetry(address)
    CodecTestKit.assertCodecSymmetry(contact)
    CodecTestKit.assertCodecSymmetry(person)

    val personBson = CodecTestKit.toBsonDocument(person)
    personBson.getString("name").getValue shouldBe "John Doe"
    personBson.getDocument("address").getString("city").getValue shouldBe "Springfield"
    personBson.getDocument("contact").getString("email").getValue shouldBe "test@example.com"
  }

  "CodecTestKit.assertBsonContains" should "verify partial BSON structure" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "TestUser", 25)

    // Only check specific fields, ignore others
    val expectedFields = Map(
      "name" -> new BsonString("TestUser"),
      "age" -> new BsonInt32(25)
    )

    noException should be thrownBy {
      CodecTestKit.assertBsonContains(user, expectedFields)
    }
  }

  "CodecTestKit.bsonEquivalent" should "ignore field order" in {
    val doc1 = new BsonDocument()
      .append("a", new BsonInt32(1))
      .append("b", new BsonString("hello"))
      .append("c", new BsonInt32(3))

    val doc2 = new BsonDocument()
      .append("c", new BsonInt32(3))
      .append("a", new BsonInt32(1))
      .append("b", new BsonString("hello"))

    CodecTestKit.bsonEquivalent(doc1, doc2) shouldBe true
  }

  it should "detect missing fields" in {
    val doc1 = new BsonDocument()
      .append("a", new BsonInt32(1))
      .append("b", new BsonString("hello"))

    val doc2 = new BsonDocument()
      .append("a", new BsonInt32(1))

    CodecTestKit.bsonEquivalent(doc1, doc2) shouldBe false
  }

  it should "detect different values" in {
    val doc1 = new BsonDocument()
      .append("a", new BsonInt32(1))

    val doc2 = new BsonDocument()
      .append("a", new BsonInt32(2))

    CodecTestKit.bsonEquivalent(doc1, doc2) shouldBe false
  }

  "CodecTestKit.checkCodecSymmetry" should "return Right for valid codec" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "ValidUser", 30)
    val result = CodecTestKit.checkCodecSymmetry(user)

    result shouldBe Right(())
  }

  "CodecTestKit.codecSymmetryProperty" should "return true for valid codec" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "PropUser", 40)
    CodecTestKit.codecSymmetryProperty(user) shouldBe true
  }

  "CodecTestKit.prettyPrint" should "format BSON documents readably" in {
    val doc = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("age", new BsonInt32(30))

    val pretty = CodecTestKit.prettyPrint(doc)

    pretty should include("name")
    pretty should include("Alice")
    pretty should include("age")
    pretty should include("30")
  }

  "CodecTestKit.diff" should "identify missing fields" in {
    val expected = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("age", new BsonInt32(30))

    val actual = new BsonDocument()
      .append("name", new BsonString("Alice"))

    val diffStr = CodecTestKit.diff(expected, actual)

    diffStr should include("Missing fields")
    diffStr should include("age")
  }

  it should "identify extra fields" in {
    val expected = new BsonDocument()
      .append("name", new BsonString("Alice"))

    val actual = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("age", new BsonInt32(30))

    val diffStr = CodecTestKit.diff(expected, actual)

    diffStr should include("Extra fields")
    diffStr should include("age")
  }

  it should "identify mismatched values" in {
    val expected = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("age", new BsonInt32(30))

    val actual = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("age", new BsonInt32(25))

    val diffStr = CodecTestKit.diff(expected, actual)

    diffStr should include("Mismatched fields")
    diffStr should include("age")
  }

  it should "return no differences for identical documents" in {
    val doc1 = new BsonDocument()
      .append("name", new BsonString("Alice"))

    val doc2 = new BsonDocument()
      .append("name", new BsonString("Alice"))

    val diffStr = CodecTestKit.diff(doc1, doc2)

    diffStr shouldBe "No differences"
  }

  "CodecTestKit.roundTripWithContext" should "use custom encoder context" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "ContextUser", 35)
    val encoderContext = EncoderContext.builder().isEncodingCollectibleDocument(true).build()
    val decoderContext = DecoderContext.builder().build()

    val result = CodecTestKit.roundTripWithContext(user, encoderContext, decoderContext)

    result shouldBe user
  }

  "CodecTestKit.assertCodecSymmetryWithContext" should "support custom contexts" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "ContextUser2", 40)
    val encoderContext = EncoderContext.builder().build()
    val decoderContext = DecoderContext.builder().build()

    noException should be thrownBy {
      CodecTestKit.assertCodecSymmetryWithContext(user, encoderContext, decoderContext)
    }
  }

  // ===== ENHANCED COMPARISON HELPERS TESTS =====

  "CodecTestKit.extractField" should "extract specific field values" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "ExtractTest", 35)
    val nameValue = CodecTestKit.extractField(user, "name")
    val ageValue = CodecTestKit.extractField(user, "age")

    nameValue shouldBe a[BsonString]
    nameValue.asString().getValue shouldBe "ExtractTest"

    ageValue shouldBe a[BsonInt32]
    ageValue.asInt32().getValue shouldBe 35
  }

  it should "return null for non-existent fields" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(new ObjectId(), "Test", 30)
    val missing = CodecTestKit.extractField(user, "nonexistent")

    missing shouldBe null
  }

  "CodecTestKit.bsonArraysEqual" should "compare arrays in order" in {
    import org.bson.BsonArray

    val arr1 = new BsonArray()
    arr1.add(new BsonString("a"))
    arr1.add(new BsonString("b"))
    arr1.add(new BsonString("c"))

    val arr2 = new BsonArray()
    arr2.add(new BsonString("a"))
    arr2.add(new BsonString("b"))
    arr2.add(new BsonString("c"))

    CodecTestKit.bsonArraysEqual(arr1, arr2) shouldBe true
  }

  it should "detect different order" in {
    import org.bson.BsonArray

    val arr1 = new BsonArray()
    arr1.add(new BsonString("a"))
    arr1.add(new BsonString("b"))

    val arr2 = new BsonArray()
    arr2.add(new BsonString("b"))
    arr2.add(new BsonString("a"))

    CodecTestKit.bsonArraysEqual(arr1, arr2) shouldBe false
  }

  "CodecTestKit.bsonArraysEquivalent" should "ignore element order" in {
    import org.bson.BsonArray

    val arr1 = new BsonArray()
    arr1.add(new BsonString("a"))
    arr1.add(new BsonString("b"))
    arr1.add(new BsonString("c"))

    val arr2 = new BsonArray()
    arr2.add(new BsonString("c"))
    arr2.add(new BsonString("a"))
    arr2.add(new BsonString("b"))

    CodecTestKit.bsonArraysEquivalent(arr1, arr2) shouldBe true
  }

  it should "detect different elements" in {
    import org.bson.BsonArray

    val arr1 = new BsonArray()
    arr1.add(new BsonString("a"))
    arr1.add(new BsonString("b"))

    val arr2 = new BsonArray()
    arr2.add(new BsonString("a"))
    arr2.add(new BsonString("c"))

    CodecTestKit.bsonArraysEquivalent(arr1, arr2) shouldBe false
  }

  "CodecTestKit.bsonDeepContains" should "verify nested field paths" in {
    val nested = new BsonDocument()
      .append("street", new BsonString("Main St"))
      .append("city", new BsonString("Springfield"))

    val doc = new BsonDocument()
      .append("name", new BsonString("John"))
      .append("address", nested)

    val expected = Map(
      "address.city" -> new BsonString("Springfield")
    )

    CodecTestKit.bsonDeepContains(doc, expected) shouldBe true
  }

  it should "detect mismatched nested values" in {
    val nested = new BsonDocument()
      .append("city", new BsonString("Springfield"))

    val doc = new BsonDocument()
      .append("address", nested)

    val expected = Map(
      "address.city" -> new BsonString("Boston")
    )

    CodecTestKit.bsonDeepContains(doc, expected) shouldBe false
  }

  "CodecTestKit.deepDiff" should "report nested differences" in {
    val expected = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("age", new BsonInt32(30))
      .append(
        "address",
        new BsonDocument()
          .append("city", new BsonString("Boston"))
      )

    val actual = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("age", new BsonInt32(25))
      .append(
        "address",
        new BsonDocument()
          .append("city", new BsonString("New York"))
      )

    val differences = CodecTestKit.deepDiff(expected, actual)

    differences should not be empty
    differences.exists(_.contains("age")) shouldBe true
    differences.exists(_.contains("address.city")) shouldBe true
  }

  it should "handle array differences" in {
    import org.bson.BsonArray

    val arr1 = new BsonArray()
    arr1.add(new BsonString("a"))
    arr1.add(new BsonString("b"))

    val arr2 = new BsonArray()
    arr2.add(new BsonString("a"))

    val expected = new BsonDocument().append("items", arr1)
    val actual = new BsonDocument().append("items", arr2)

    val differences = CodecTestKit.deepDiff(expected, actual)

    differences should not be empty
    differences.exists(_.contains("items")) shouldBe true
  }

  it should "return empty list for identical documents" in {
    val doc = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("age", new BsonInt32(30))

    val differences = CodecTestKit.deepDiff(doc, doc)

    differences shouldBe empty
  }

  "CodecTestKit.bsonValuesEqual" should "handle nested documents" in {
    val doc1 = new BsonDocument()
      .append(
        "inner",
        new BsonDocument()
          .append("value", new BsonInt32(42))
      )

    val doc2 = new BsonDocument()
      .append(
        "inner",
        new BsonDocument()
          .append("value", new BsonInt32(42))
      )

    CodecTestKit.bsonValuesEqual(doc1, doc2) shouldBe true
  }

  it should "handle arrays recursively" in {
    import org.bson.BsonArray

    val inner1 = new BsonDocument().append("x", new BsonInt32(1))
    val inner2 = new BsonDocument().append("x", new BsonInt32(1))

    val arr1 = new BsonArray()
    arr1.add(inner1)

    val arr2 = new BsonArray()
    arr2.add(inner2)

    CodecTestKit.bsonValuesEqual(arr1, arr2) shouldBe true
  }

end CodecTestKitSpec
