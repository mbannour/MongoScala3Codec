package io.github.mbannour.mongo.codecs

import scala.compiletime.testing.{Error, typeCheckErrors}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The compile-time diagnostics are part of the published API: a model this library refuses is refused with a message, and that message is
  * all the user gets. This spec holds the properties every controlled rejection shares, so that a new validation cannot be added with a
  * message that leaves the user guessing which library spoke or what to do about it.
  *
  * The per-case specs - `BsonIgnoreValidationSpec`, `BsonIdValidationSpec`, `DuplicateDiscriminatorSpec` and the rest - own the details of
  * what each message must say. This one deliberately asserts only the shared contract, over one representative of each rejection.
  *
  * Fragments are asserted, never whole messages: wording and layout are free to improve, and the compiler's own rendering around the
  * message varies between Scala versions.
  */
class DiagnosticContractSpec extends AnyFlatSpec with Matchers:

  private def messageOf(errors: List[Error]): String = errors.map(_.message).mkString("\n")

  /** One representative snippet per controlled rejection, each labelled by the rule it violates. */
  private val diagnostics: List[(String, List[Error])] = List(
    "@BsonIgnore without a constructor default" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.bson.macros.BsonIgnore
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class ContractIgnore(name: String, @BsonIgnore internalState: String)

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).register[ContractIgnore].build
    """),
    "multiple @BsonId fields" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import org.bson.types.ObjectId
      import io.github.mbannour.bson.macros.BsonId
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class ContractTwoIds(@BsonId primaryId: ObjectId, @BsonId legacyId: ObjectId)

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).register[ContractTwoIds].build
    """),
    "@BsonId and @BsonIgnore on one field" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import org.bson.types.ObjectId
      import io.github.mbannour.bson.macros.{BsonId, BsonIgnore}
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class ContractConflict(@BsonId @BsonIgnore id: ObjectId = new ObjectId())

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).register[ContractConflict].build
    """),
    "path-dependent model" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      class ContractContainer:
        case class Paint(name: String)

      val container = new ContractContainer

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).register[container.Paint].build
    """),
    "duplicate discriminator value" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.bson.macros.BsonDiscriminator
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      sealed trait ContractAnimal
      @BsonDiscriminator("animal") case class ContractDog(name: String) extends ContractAnimal
      @BsonDiscriminator("animal") case class ContractCat(name: String) extends ContractAnimal

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).registerSealed[ContractAnimal].build
    """),
    "empty discriminator value" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.bson.macros.BsonDiscriminator
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      sealed trait ContractVehicle
      @BsonDiscriminator("") case class ContractBus(name: String) extends ContractVehicle
      case class ContractCar(name: String) extends ContractVehicle

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).registerSealed[ContractVehicle].build
    """),
    "unsupported field type" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class ContractReading(label: String, point: (String, Int))

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).register[ContractReading].build
    """),
    "non-String map key" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class ContractKeyed(scores: Map[Int, String])

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).register[ContractKeyed].build
    """),
    "a type that is not a case class" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      class ContractPlain(val name: String)

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).register[ContractPlain].build
    """),
    "a sealed hierarchy with no case class subtypes" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      sealed trait ContractEmpty

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).registerSealed[ContractEmpty].build
    """),
    "registerSealed on a case class" -> typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class ContractConcrete(name: String)

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).registerSealed[ContractConcrete].build
    """)
  )

  private def forEachDiagnostic(check: (String, String) => Unit): Unit =
    diagnostics.foreach { (label, errors) =>
      withClue(s"'$label' produced no compile error at all: ") {
        errors should not be empty
      }
      val message = messageOf(errors)
      withClue(s"diagnostic for '$label' was:\n$message\n") {
        check(label, message)
      }
    }

  "Every controlled rejection" should "name MongoScala3Codec, so the user knows which library refused the model" in {
    forEachDiagnostic((_, message) => message should include("MongoScala3Codec"))
  }

  it should "render every value it interpolates, leaking no un-substituted placeholder" in {
    // A missing `s` prefix on an interpolated string is invisible in the source and silently prints
    // the placeholder to the user, so it is asserted rather than left to review.
    forEachDiagnostic((_, message) => message should not include "$")
  }

  it should "stay in the user's vocabulary, exposing no macro or compiler internals" in {
    // Named phrases rather than bare words: `ClassTag` is ordinary Scala vocabulary that a future
    // message may legitimately want, whereas "Cannot summon ClassTag" is the macro talking to itself.
    val internals =
      List(
        "ExprCastException",
        "quotes.reflect",
        "scala.quoted",
        "MatchError",
        "TreeMap",
        "Cannot summon ClassTag",
        "Cannot find ClassTag",
        "TypeRepr",
        "errorAndAbort"
      )

    forEachDiagnostic { (_, message) =>
      internals.foreach(internal => message should not include internal)
    }
  }

  "Registering a Scala 3 enum as a case class" should "point at the enum codec provider rather than at case classes" in {
    // Enums are supported, through EnumValueCodecProvider rather than through `register`. Telling the
    // user to rewrite a supported type as a case class would send them away from working code.
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      enum ContractColour:
        case Red, Green

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).register[ContractColour].build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message should include("ContractColour")
      message.toLowerCase should include("enum")
      message should include("EnumValueCodecProvider")
      message should not include "case class ContractColour"
    }
  }

  "Registering a trait" should "point at sealed hierarchies rather than at making the trait a case class" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      trait ContractTrait

      RegistryBuilder.from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())).register[ContractTrait].build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message should include("ContractTrait")
      message.toLowerCase should include("trait")
      message should include("sealed")
      message should not include "case class ContractTrait"
    }
  }
end DiagnosticContractSpec
