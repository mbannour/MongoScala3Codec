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
end BsonIgnoreSpec
