package io.github.mbannour.mongo.codecs.compatibility

import org.bson.{BsonDocument, BsonNull, BsonString}
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** BSON compatibility test for `Option[T]` fields, under the library's default `NoneHandling` (`NoneHandling.Encode`). See
  * [[UserCompatibilitySpec]] for the package-level contract.
  */
class OptionCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class UserWithEmail(name: String, email: Option[String])

  private val registry = RegistryBuilder
    .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
    .register[UserWithEmail]
    .build

  given codec: Codec[UserWithEmail] = registry.get(classOf[UserWithEmail])

  "UserWithEmail codec" should "encode Some(email) as the raw string value, and round-trip to an equal value" in {
    val user = UserWithEmail("Alice", Some("alice@example.com"))

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("email", new BsonString("alice@example.com"))

    CodecTestKit.assertBsonStructure(user, expectedBson)
    CodecTestKit.fromBsonDocument[UserWithEmail](expectedBson) shouldBe user
    CodecTestKit.roundTrip(user) shouldBe user
  }

  it should "encode None as an explicit BSON null under the default NoneHandling.Encode, and round-trip to an equal value" in {
    val user = UserWithEmail("Alice", None)

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("email", new BsonNull())

    CodecTestKit.assertBsonStructure(user, expectedBson)
    CodecTestKit.fromBsonDocument[UserWithEmail](expectedBson) shouldBe user
    CodecTestKit.roundTrip(user) shouldBe user
  }

  // An absent key and an explicit null both mean None on the way in, so documents written under
  // NoneHandling.Ignore stay readable by a reader configured with the default NoneHandling.Encode.
  it should "also decode None from a document that omits the field entirely" in {
    val writtenWithNoneHandlingIgnore = new BsonDocument()
      .append("name", new BsonString("Alice"))

    CodecTestKit.fromBsonDocument[UserWithEmail](writtenWithNoneHandlingIgnore) shouldBe UserWithEmail("Alice", None)
  }
end OptionCompatibilitySpec
