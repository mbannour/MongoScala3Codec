package io.github.mbannour.mongo.codecs

import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecRegistry, CodecRegistries}
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonString, BsonInt32, BsonObjectId}
import org.bson.codecs.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import RegistryBuilder.*

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
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
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
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
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

    case class Address(_id: ObjectId, street: String, city: String)
    case class Contact(_id: ObjectId, email: String, phone: Option[String])
    case class Person(_id: ObjectId, name: String, address: Option[Address], contact: Contact)

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
end CodecTestKitSpec
