package io.github.mbannour.mongo.codecs.compatibility

import org.bson.{BsonDocument, BsonString}
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonIgnore
import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** BSON compatibility test for `@BsonIgnore`. See [[UserCompatibilitySpec]] for the package-level contract.
  *
  * A `@BsonIgnore`-annotated field must never appear in the encoded document, and must decode back to its constructor default. A future
  * macro refactor must not accidentally start persisting this field.
  */
class BsonIgnoreCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class User(name: String, @BsonIgnore internalNote: String = "")

  private val registry = RegistryBuilder
    .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
    .register[User]
    .build

  given codec: Codec[User] = registry.get(classOf[User])

  "User codec with a @BsonIgnore field" should "encode to the frozen BSON representation, omitting the ignored field" in {
    val user = User("Alice", "secret")

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("Alice"))

    CodecTestKit.assertBsonStructure(user, expectedBson)
  }

  it should "round-trip to a value with the ignored field reset to its constructor default" in {
    val user = User("Alice", "secret")

    CodecTestKit.roundTrip(user) shouldBe User("Alice", "")
  }

  case class Settings(
      name: String,
      @BsonIgnore retryCount: Int = 5,
      @BsonIgnore environment: String = "production"
  )

  "Settings codec with multiple @BsonIgnore fields" should "encode to the frozen BSON representation, omitting every ignored field" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.IntegerCodec()))
      .register[Settings]
      .build

    given codec: Codec[Settings] = registry.get(classOf[Settings])

    val settings = Settings("payments", 99, "staging")

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("payments"))

    CodecTestKit.assertBsonStructure(settings, expectedBson)
    // Non-trivial defaults are restored on decode, not zero values.
    CodecTestKit.roundTrip(settings) shouldBe Settings("payments", 5, "production")
  }

  case class Address(city: String, @BsonIgnore cacheKey: String = "unknown")
  case class UserWithAddress(name: String, address: Address)

  "UserWithAddress codec" should "encode to the frozen BSON representation, omitting the nested ignored field" in {
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
    CodecTestKit.roundTrip(user) shouldBe UserWithAddress("Alice", Address("Munich", "unknown"))
  }
end BsonIgnoreCompatibilitySpec
