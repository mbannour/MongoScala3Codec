package io.github.mbannour.mongo.codecs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RegistryBuilderDuplicateCodecIntegrationSpec extends AnyFlatSpec with Matchers:

  private inline def assertDuplicate(
      inline code: String,
      targetType: String,
      expectedSnippets: Seq[String] = Seq.empty
  ): Unit =
    val errors = scala.compiletime.testing.typeCheckErrors(code)
    errors.nonEmpty shouldBe true
    val message = errors.map(_.message).mkString("\n")
    message should include(s"Duplicate codec detected for $targetType")
    expectedSnippets.foreach(snippet => message should include(snippet))
  end assertDuplicate

  "RegistryBuilder duplicate detection" should "reject explicit + derived for register[T]" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.Codec
        import org.bson.codecs.configuration.CodecRegistries

        case class RoleAssignment(role: String)

        RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .withCodec(null.asInstanceOf[Codec[RoleAssignment]])
          .register[RoleAssignment]
      """,
      targetType = "RoleAssignment",
      expectedSnippets = Seq("already registered in this builder")
    )
  }

  it should "reject explicit + derived for registerAll[Tuple]" in {
    assertDuplicate(
      """
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
      """,
      targetType = "RoleAssignment",
      expectedSnippets = Seq("already registered in this builder")
    )
  }

  it should "reject explicit + explicit with withCodec" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.Codec
        import org.bson.codecs.configuration.CodecRegistries

        case class RoleAssignment(role: String)

        RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .withCodec(null.asInstanceOf[Codec[RoleAssignment]])
          .withCodec(null.asInstanceOf[Codec[RoleAssignment]])
      """,
      targetType = "RoleAssignment",
      expectedSnippets = Seq("already tracked in this builder")
    )
  }

  it should "reject explicit + explicit with withCodecs varargs" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.Codec
        import org.bson.codecs.configuration.CodecRegistries

        case class RoleAssignment(role: String)

        RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .withCodecs(
            null.asInstanceOf[Codec[RoleAssignment]],
            null.asInstanceOf[Codec[RoleAssignment]]
          )
      """,
      targetType = "RoleAssignment",
      expectedSnippets = Seq("already tracked in this builder")
    )
  }

  it should "reject derived + derived with repeated register[T]" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.configuration.CodecRegistries

        case class RoleAssignment(role: String)

        RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .register[RoleAssignment]
          .register[RoleAssignment]
      """,
      targetType = "RoleAssignment",
      expectedSnippets = Seq("already registered in this builder")
    )
  }

  it should "reject overlap between register[T] and registerAll[Tuple]" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.configuration.CodecRegistries

        case class RoleAssignment(role: String)
        case class User(roleAssignment: RoleAssignment)

        RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .register[RoleAssignment]
          .registerAll[(User, RoleAssignment)]
      """,
      targetType = "RoleAssignment",
      expectedSnippets = Seq("already registered in this builder")
    )
  }

  it should "reject duplicate entries inside registerAll tuple" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.configuration.CodecRegistries

        case class RoleAssignment(role: String)

        RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .registerAll[(RoleAssignment, RoleAssignment)]
      """,
      targetType = "RoleAssignment",
      expectedSnippets = Seq("appears more than once")
    )
  }

  it should "reject explicit + derived for registerSealed[T]" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.Codec
        import org.bson.codecs.configuration.CodecRegistries

        sealed trait Status
        case class Active() extends Status
        case class Inactive() extends Status

        RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .withCodec(null.asInstanceOf[Codec[Status]])
          .registerSealed[Status]
      """,
      targetType = "Status",
      expectedSnippets = Seq("already registered in this builder")
    )
  }

  it should "reject duplicate entries inside registerSealedAll tuple" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.configuration.CodecRegistries

        sealed trait Status
        case class Active() extends Status
        case class Inactive() extends Status

        RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .registerSealedAll[(Status, Status)]
      """,
      targetType = "Status",
      expectedSnippets = Seq("appears more than once")
    )
  }

  it should "reject overlap between registerSealed[T] and registerSealedAll[Tuple]" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.configuration.CodecRegistries

        sealed trait Status
        case class Active() extends Status
        case class Inactive() extends Status

        RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .registerSealed[Status]
          .registerSealedAll[Tuple1[Status]]
      """,
      targetType = "Status",
      expectedSnippets = Seq("already registered in this builder")
    )
  }

  it should "reject duplicates when merging builders with ++" in {
    assertDuplicate(
      """
        import io.github.mbannour.mongo.codecs.RegistryBuilder
        import io.github.mbannour.mongo.codecs.RegistryBuilder.*
        import org.bson.codecs.configuration.CodecRegistries

        case class RoleAssignment(role: String)
        case class User(roleAssignment: RoleAssignment)

        val left = RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .register[RoleAssignment]

        val right = RegistryBuilder
          .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
          .registerAll[(User, RoleAssignment)]

        left ++ right
      """,
      targetType = "RoleAssignment",
      expectedSnippets = Seq("registered in both the left and right builder")
    )
  }

end RegistryBuilderDuplicateCodecIntegrationSpec
