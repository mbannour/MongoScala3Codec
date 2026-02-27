package io.github.mbannour.mongo.codecs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RegistryBuilderDuplicateCodecCompileTimeSpec extends AnyFlatSpec with Matchers:

  "RegistryBuilder" should "fail at compile time when an explicit codec is also auto-derived via registerAll" in {
    val errors = scala.compiletime.testing.typeCheckErrors("""
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder.*
      import org.bson.codecs.Codec
      import org.bson.codecs.configuration.CodecRegistries

      case class RoleAssignment(role: String)
      case class User(roleAssignment: RoleAssignment)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .withCodec(null.asInstanceOf[Codec[RoleAssignment]])
        .registerAll[(User, RoleAssignment)]
    """)

    errors.exists(_.message.contains("Duplicate codec detected for RoleAssignment")) shouldBe true
    errors.exists(_.message.contains("already registered in this builder")) shouldBe true
  }

  it should "fail at compile time when duplicate types appear inside registerAll tuple" in {
    val errors = scala.compiletime.testing.typeCheckErrors("""
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder.*
      import org.bson.codecs.configuration.CodecRegistries

      case class RoleAssignment(role: String)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .registerAll[(RoleAssignment, RoleAssignment)]
    """)

    errors.exists(_.message.contains("Duplicate codec detected for RoleAssignment")) shouldBe true
    errors.exists(_.message.contains("appears more than once")) shouldBe true
  }

  it should "fail at compile time when the same explicit codec type is registered twice" in {
    val errors = scala.compiletime.testing.typeCheckErrors("""
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder.*
      import org.bson.codecs.Codec
      import org.bson.codecs.configuration.CodecRegistries

      case class RoleAssignment(role: String)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .withCodec(null.asInstanceOf[Codec[RoleAssignment]])
        .withCodec(null.asInstanceOf[Codec[RoleAssignment]])
    """)

    errors.exists(_.message.contains("Duplicate codec detected for RoleAssignment")) shouldBe true
    errors.exists(_.message.contains("already tracked in this builder")) shouldBe true
  }

  it should "also detect explicit+derived duplicates when using withCodecs varargs" in {
    val errors = scala.compiletime.testing.typeCheckErrors("""
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder.*
      import org.bson.codecs.Codec
      import org.bson.codecs.configuration.CodecRegistries

      case class RoleAssignment(role: String)
      case class User(roleAssignment: RoleAssignment)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .withCodecs(
          null.asInstanceOf[Codec[String]],
          null.asInstanceOf[Codec[RoleAssignment]]
        )
        .registerAll[(User, RoleAssignment)]
    """)

    errors.exists(_.message.contains("Duplicate codec detected for RoleAssignment")) shouldBe true
  }

end RegistryBuilderDuplicateCodecCompileTimeSpec
