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
end BsonIgnoreCompatibilitySpec
