package io.github.mbannour.mongo.codecs

import scala.compiletime.testing.typeCheckErrors

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Compile-time contract for path-dependent types.
  *
  * A model reached through a specific instance (`c.Paint` for some `val c`) cannot be derived: the generated trees reference the enclosing
  * class through `this`, which is not the same type as `c.Paint`. That is a real limitation, but it must surface as a MongoScala3Codec
  * diagnostic rather than as a macro-expansion crash.
  *
  * `typeCheckErrors` only accepts a string literal, so the `Container` declaration is repeated in each case rather than shared.
  */
class PathDependentTypeValidationSpec extends AnyFlatSpec with Matchers:

  "Registering a case class reached through an instance" should "fail to compile with an actionable diagnostic" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      class Container:
        enum Colour:
          case Red, Green

        case class Paint(name: String, colour: Colour)

      val c = new Container

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[c.Paint]
        .build
    """)

    errors should not be empty

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message should include("Paint")
      message.toLowerCase should include("path-dependent")
      message.toLowerCase should include("unsupported")
      message should not include "ExprCastException"
    }
  }

  "Deriving an enum codec provider for an enum reached through an instance" should "fail to compile with an actionable diagnostic" in {
    val errors = typeCheckErrors("""
      import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

      class Container:
        enum Colour:
          case Red, Green

      val c = new Container

      EnumValueCodecProvider.forStringEnum[c.Colour]
    """)

    errors should not be empty

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message should include("Colour")
      message.toLowerCase should include("path-dependent")
      message.toLowerCase should include("unsupported")
      message should not include "ExprCastException"
    }
  }

  it should "fail the same way for an ordinal enum" in {
    val errors = typeCheckErrors("""
      import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

      class Container:
        enum Colour:
          case Red, Green

      val c = new Container

      EnumValueCodecProvider.forOrdinalEnum[c.Colour]
    """)

    errors should not be empty

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message.toLowerCase should include("path-dependent")
      message should not include "ExprCastException"
    }
  }

  /** `typeCheckErrors` compiles its snippet inside this method, so an `object` declared in it is a *method-local* object, not a top-level
    * one. Case-class derivation handles that: the generated trees reach the companion without an outer reference.
    */
  "A case class nested in an object" should "still derive, since an object is a stable path" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      object Domain:
        case class Paint(name: String)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[Domain.Paint]
        .build
    """)

    withClue(s"unexpected errors:\n${errors.map(_.message).mkString("\n")}\n") {
      errors shouldBe empty
    }
  }

  /** Characterized, not endorsed: enum derivation does *not* handle a method-local object today.
    *
    * `EnumCodecGenerator` emits `Domain.this.Colour.values`, and a local object has no `this` reachable from the expansion site, so the
    * compiler rejects the tree. Enums nested in genuine top-level objects are unaffected and are covered by [[NestedEnumSpec]], including
    * an object inside an object - so this is about locality, not nesting.
    *
    * The failure is uniform across every supported compiler, though where it surfaces is not: on 3.7.1 the snippet type-checks clean and
    * the same code fails later with a compiler assertion. The assertion below is therefore only that derivation does not silently succeed.
    */
  "An enum nested in a method-local object" should "not derive today, and must not crash with a raw macro cast error" in {
    val errors = typeCheckErrors("""
      import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

      object Domain:
        enum Colour:
          case Red, Green

      EnumValueCodecProvider.forStringEnum[Domain.Colour]
    """)

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message should not include "ExprCastException"
    }
  }

  // The owner chain cannot tell this case from `c.Paint` - both are owned by a plain class. Only the
  // prefix can: here it is the enclosing `this`, which the generated trees reproduce exactly.
  "A model declared in a class body and used from inside that class" should "still derive" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{EnumValueCodecProvider, RegistryBuilder}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      class Container:
        enum Colour:
          case Red, Green

        case class Paint(name: String, colour: Colour)

        val registry = RegistryBuilder
          .from(
            CodecRegistries.fromRegistries(
              CodecRegistries.fromProviders(EnumValueCodecProvider.forStringEnum[Colour]),
              CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())
            )
          )
          .register[Paint]
          .build

      new Container
    """)

    withClue(s"unexpected errors:\n${errors.map(_.message).mkString("\n")}\n") {
      errors shouldBe empty
    }
  }
end PathDependentTypeValidationSpec
