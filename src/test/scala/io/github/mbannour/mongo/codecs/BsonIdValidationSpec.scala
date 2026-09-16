package io.github.mbannour.mongo.codecs

import scala.compiletime.testing.typeCheckErrors

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonId
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** Compile-time contract for `@BsonId`: a MongoDB document has exactly one `_id`, so a product type may annotate at most one field. */
class BsonIdValidationSpec extends AnyFlatSpec with Matchers:

  case class NoId(name: String)
  case class OneId(@BsonId id: ObjectId, name: String)
  case class NamedDifferently(id: String, @BsonId mongoId: ObjectId)

  private val baseRegistry =
    CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.ObjectIdCodec())

  "A type with no @BsonId field" should "derive successfully" in {
    val registry = RegistryBuilder.from(baseRegistry).register[NoId].build
    val codec: Codec[NoId] = registry.get(classOf[NoId])

    codec.getEncoderClass shouldBe classOf[NoId]
  }

  "A type with exactly one @BsonId field" should "derive successfully" in {
    val registry = RegistryBuilder.from(baseRegistry).register[OneId].build
    val codec: Codec[OneId] = registry.get(classOf[OneId])

    codec.getEncoderClass shouldBe classOf[OneId]
  }

  "A type with an ordinary id field and a differently named @BsonId field" should "derive successfully" in {
    val registry = RegistryBuilder.from(baseRegistry).register[NamedDifferently].build
    val codec: Codec[NamedDifferently] = registry.get(classOf[NamedDifferently])

    codec.getEncoderClass shouldBe classOf[NamedDifferently]
  }

  it should "map the ordinary field to id and the annotated field to _id" in {
    val registry = RegistryBuilder.from(baseRegistry).register[NamedDifferently].build

    given codec: Codec[NamedDifferently] = registry.get(classOf[NamedDifferently])

    val fixedId = new ObjectId("507f1f77bcf86cd799439011")
    val bson = CodecTestKit.toBsonDocument(NamedDifferently("u-1", fixedId))

    bson.getString("id").getValue shouldBe "u-1"
    bson.getObjectId("_id").getValue shouldBe fixedId
    bson.keySet().size() shouldBe 2
  }

  "A type declaring two @BsonId fields" should "fail to compile with an actionable diagnostic" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import org.bson.types.ObjectId
      import io.github.mbannour.bson.macros.BsonId
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class InvalidUser(@BsonId primaryId: ObjectId, @BsonId legacyId: ObjectId, name: String)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.ObjectIdCodec()))
        .register[InvalidUser]
        .build
    """)

    errors should not be empty

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message should include("@BsonId")
      message should include("primaryId")
      message should include("legacyId")
      message.toLowerCase should include("only one")
    }
  }
end BsonIdValidationSpec
