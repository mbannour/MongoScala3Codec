package io.github.mbannour.mongo.codecs

import scala.compiletime.testing.typeCheckErrors

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonIgnore
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** Compile-time contract for `@BsonIgnore`: an ignored field is never read from BSON, so it must carry a constructor default the decoder
  * can fall back to. A model without one is a definition error and must be rejected during derivation.
  */
class BsonIgnoreValidationSpec extends AnyFlatSpec with Matchers:

  case class ValidUser(name: String, @BsonIgnore internalNote: String = "")

  "@BsonIgnore on a field with a constructor default" should "derive successfully" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
      .register[ValidUser]
      .build

    val codec: Codec[ValidUser] = registry.get(classOf[ValidUser])

    codec.getEncoderClass shouldBe classOf[ValidUser]
  }

  it should "report no compile errors when derived from a type-checked snippet" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.bson.macros.BsonIgnore
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class ValidSnippetUser(name: String, @BsonIgnore internalNote: String = "")

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[ValidSnippetUser]
        .build
    """)

    errors shouldBe empty
  }

  "@BsonIgnore on a field without a constructor default" should "fail to compile with an actionable diagnostic" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.bson.macros.BsonIgnore
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class InvalidUser(name: String, @BsonIgnore internalNote: String)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[InvalidUser]
        .build
    """)

    errors should not be empty

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message should include("@BsonIgnore")
      message should include("internalNote")
      message should include("default")
      // Actionable guidance, with the field type rendered in source form.
      message should include("@BsonIgnore internalNote: String = <default>")
    }
  }

  it should "name only the offending field when another ignored field is valid" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.bson.macros.BsonIgnore
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class PartiallyInvalidUser(name: String, @BsonIgnore cache: String = "", @BsonIgnore runtimeState: Int)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.IntegerCodec()))
        .register[PartiallyInvalidUser]
        .build
    """)

    errors should not be empty

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message should include("@BsonIgnore")
      message should include("runtimeState")
      message should include("default")
      // The field that does have a default must not be blamed.
      message should not include "cache"
    }
  }
end BsonIgnoreValidationSpec
