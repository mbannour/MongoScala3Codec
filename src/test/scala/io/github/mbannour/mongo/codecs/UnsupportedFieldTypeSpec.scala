package io.github.mbannour.mongo.codecs

import scala.compiletime.testing.{Error, typeCheckErrors}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

case class Wrapper[A](value: A)

/** Some field types cannot be derived whatever codecs the registry holds, because the shape itself carries no runtime class to encode
  * against. Those must be rejected while compiling, naming the model, the field and the type.
  *
  * This is deliberately narrow. A field type that merely has no codec *yet* - `scala.math.BigDecimal`, `Array[String]`, `Either` - is not
  * rejected here: the registry is a runtime value, so a user or driver codec can still supply one, and rejecting them would turn a missing
  * codec into a permanent verdict.
  */
class UnsupportedFieldTypeSpec extends AnyFlatSpec with Matchers:

  private def messageOf(errors: List[Error]): String = errors.map(_.message).mkString("\n")

  "A tuple field" should "fail to compile, naming the model, the field and the type" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class Reading(label: String, point: (String, Int))

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[Reading]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message should include("Reading")
      message should include("point")
      message.toLowerCase should include("tuple")
      message.toLowerCase should include("unsupported")
      message should not include "Cannot summon ClassTag"
    }
  }

  "A field whose type is a type parameter" should "fail to compile, naming the model, the field and the type" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{RegistryBuilder, Wrapper}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[Wrapper[String]]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message should include("Wrapper")
      message should include("value")
      message.toLowerCase should include("unsupported")
      message should not include "Cannot summon ClassTag"
    }
  }

  "A tuple inside a collection field" should "fail to compile with the same information as a bare tuple field" in {
    // A tuple reached through a type argument is rejected on a different code path than a bare tuple
    // field, so the two are asserted separately: both are the same mistake and must read the same way.
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class Track(label: String, points: List[(String, Int)])

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[Track]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message should include("Track")
      message should include("points")
      message.toLowerCase should include("tuple")
      message.toLowerCase should include("unsupported")
      message should include("MongoScala3Codec")
    }
  }
end UnsupportedFieldTypeSpec
