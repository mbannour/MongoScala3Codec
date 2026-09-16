package io.github.mbannour.mongo.codecs.compatibility

import org.bson.BsonDocument
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** Characterizes current decode behavior when a BSON document predates a field that has a constructor default (e.g. an older document
  * written before `status` was added to the case class). See [[UserCompatibilitySpec]] for the package-level contract.
  *
  * This does not assert what the roadmap eventually wants; it freezes what the codec does today.
  */
class ConstructorDefaultCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class UserWithStatus(name: String, status: String = "active")

  private val registry = RegistryBuilder
    .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
    .register[UserWithStatus]
    .build

  given codec: Codec[UserWithStatus] = registry.get(classOf[UserWithStatus])

  "UserWithStatus codec" should "apply the constructor default when decoding a document missing the status field" in {
    val legacyBson = new BsonDocument()
      .append("name", new org.bson.BsonString("Alice"))

    val decoded = CodecTestKit.fromBsonDocument[UserWithStatus](legacyBson)

    decoded shouldBe UserWithStatus("Alice", "active")
  }
end ConstructorDefaultCompatibilitySpec
