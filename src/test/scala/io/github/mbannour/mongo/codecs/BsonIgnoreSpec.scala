package io.github.mbannour.mongo.codecs

import org.bson.{BsonDocument, BsonString}
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonIgnore
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** Focused tests for `@BsonIgnore`: a field so annotated must never be written, and must be decoded using its constructor default. */
class BsonIgnoreSpec extends AnyFlatSpec with Matchers:

  case class User(name: String, @BsonIgnore internalNote: String = "")

  private val registry = RegistryBuilder
    .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
    .register[User]
    .build

  given codec: Codec[User] = registry.get(classOf[User])

  "Encoding a @BsonIgnore field" should "omit it from the encoded BSON document" in {
    val user = User("Alice", "private")

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("Alice"))

    CodecTestKit.assertBsonStructure(user, expectedBson)
  }

  "Decoding a document missing a @BsonIgnore field" should "restore the constructor default" in {
    val bson = new BsonDocument()
      .append("name", new BsonString("Alice"))

    val decoded = CodecTestKit.fromBsonDocument[User](bson)

    decoded shouldBe User("Alice", "")
  }

  case class UserWithTwoIgnored(name: String, @BsonIgnore internalNote: String = "", @BsonIgnore loginAttempts: Int = 0)

  "Multiple @BsonIgnore fields" should "all be omitted from the encoded document, and all restored from their defaults" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.IntegerCodec()))
      .register[UserWithTwoIgnored]
      .build

    given codec: Codec[UserWithTwoIgnored] = registry.get(classOf[UserWithTwoIgnored])

    val user = UserWithTwoIgnored("Alice", "secret", 42)

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("Alice"))

    CodecTestKit.assertBsonStructure(user, expectedBson)
    CodecTestKit.fromBsonDocument[UserWithTwoIgnored](expectedBson) shouldBe UserWithTwoIgnored("Alice", "", 0)
  }

  case class Configuration(
      name: String,
      @BsonIgnore retryCount: Int = 5,
      @BsonIgnore environment: String = "production",
      @BsonIgnore enabled: Boolean = true
  )

  "Decoding @BsonIgnore fields with non-trivial defaults" should "restore the actual constructor defaults, not zero values" in {
    val registry = RegistryBuilder
      .from(
        CodecRegistries.fromCodecs(
          new org.bson.codecs.StringCodec(),
          new org.bson.codecs.IntegerCodec(),
          new org.bson.codecs.BooleanCodec()
        )
      )
      .register[Configuration]
      .build

    given codec: Codec[Configuration] = registry.get(classOf[Configuration])

    val bson = new BsonDocument()
      .append("name", new BsonString("payments"))

    val decoded = CodecTestKit.fromBsonDocument[Configuration](bson)

    decoded shouldBe Configuration("payments", 5, "production", true)
    // Guard against zero/empty/false being synthesised instead of the declared defaults.
    decoded.retryCount shouldBe 5
    decoded.environment shouldBe "production"
    decoded.enabled shouldBe true
  }

  it should "omit ignored fields even when they hold values differing from their defaults" in {
    val registry = RegistryBuilder
      .from(
        CodecRegistries.fromCodecs(
          new org.bson.codecs.StringCodec(),
          new org.bson.codecs.IntegerCodec(),
          new org.bson.codecs.BooleanCodec()
        )
      )
      .register[Configuration]
      .build

    given codec: Codec[Configuration] = registry.get(classOf[Configuration])

    val config = Configuration("payments", 99, "staging", false)

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("payments"))

    CodecTestKit.assertBsonStructure(config, expectedBson)
  }

  case class Address(city: String, @BsonIgnore cacheKey: String = "unknown")
  case class UserWithAddress(name: String, address: Address)

  "A @BsonIgnore field inside a nested case class" should "be omitted from the nested document and restored on decode" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
      .register[Address]
      .register[UserWithAddress]
      .build

    given codec: Codec[UserWithAddress] = registry.get(classOf[UserWithAddress])

    val user = UserWithAddress("Alice", Address("Munich", "secret-cache"))

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("address", new BsonDocument().append("city", new BsonString("Munich")))

    CodecTestKit.assertBsonStructure(user, expectedBson)
    CodecTestKit.fromBsonDocument[UserWithAddress](expectedBson) shouldBe UserWithAddress("Alice", Address("Munich", "unknown"))
  }

  case class Account(name: String, status: String = "active", @BsonIgnore runtimeState: String = "idle")

  "A model mixing an ordinary default with an ignored default" should "persist the ordinary field and omit only the ignored one" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
      .register[Account]
      .build

    given codec: Codec[Account] = registry.get(classOf[Account])

    val account = Account("Alice", "suspended", "busy")

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("status", new BsonString("suspended"))

    CodecTestKit.assertBsonStructure(account, expectedBson)
    CodecTestKit.fromBsonDocument[Account](expectedBson) shouldBe Account("Alice", "suspended", "idle")
  }

  it should "still apply the ordinary field's default when that field is absent from the document" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
      .register[Account]
      .build

    given codec: Codec[Account] = registry.get(classOf[Account])

    val bson = new BsonDocument()
      .append("name", new BsonString("Alice"))

    CodecTestKit.fromBsonDocument[Account](bson) shouldBe Account("Alice", "active", "idle")
  }
end BsonIgnoreSpec
