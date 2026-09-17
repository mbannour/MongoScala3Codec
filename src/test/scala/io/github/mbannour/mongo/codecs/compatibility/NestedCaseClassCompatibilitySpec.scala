package io.github.mbannour.mongo.codecs.compatibility

import org.bson.{BsonDocument, BsonString}
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** BSON compatibility test for a nested case class. See [[UserCompatibilitySpec]] for the package-level contract. */
class NestedCaseClassCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class Address(city: String, zipCode: String)
  case class User(name: String, address: Address)

  "User codec with a nested Address" should "encode to the frozen BSON representation and round-trip to an equal value" in {
    val baseRegistry = CodecRegistries.fromCodecs(
      new org.bson.codecs.StringCodec()
    )

    val registry = RegistryBuilder
      .from(baseRegistry)
      .register[Address]
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User("Alice", Address("Munich", "80331"))

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append(
        "address",
        new BsonDocument()
          .append("city", new BsonString("Munich"))
          .append("zipCode", new BsonString("80331"))
      )

    CodecTestKit.assertBsonStructure(user, expectedBson)
    CodecTestKit.fromBsonDocument[User](expectedBson) shouldBe user
    CodecTestKit.roundTrip(user) shouldBe user
  }
end NestedCaseClassCompatibilitySpec
