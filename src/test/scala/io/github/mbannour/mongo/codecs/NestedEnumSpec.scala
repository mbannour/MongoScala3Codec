package io.github.mbannour.mongo.codecs

import org.bson.BsonDocument
import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

object NestedDomain:

  enum Colour:
    case Red, Green, Blue

  case class Paint(name: String, colour: Colour)
end NestedDomain

object Company:
  object Domain:

    enum Shade:
      case Light, Dark

    case class Wall(name: String, shade: Shade)
  end Domain
end Company

/** A Scala 3 enum that encodes through the supported provider must also decode, whether it is declared at the top level or nested inside an
  * object.
  */
class NestedEnumSpec extends AnyFlatSpec with Matchers:

  import NestedDomain.{Colour, Paint}

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec()
  )

  private def stringRegistry: CodecRegistry =
    val base = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(EnumValueCodecProvider.forStringEnum[Colour]),
      primitives
    )
    RegistryBuilder.from(base).register[Paint].build

  private def ordinalRegistry: CodecRegistry =
    val base = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(EnumValueCodecProvider.forOrdinalEnum[Colour]),
      primitives
    )
    RegistryBuilder.from(base).register[Paint].build

  "An object-nested string enum" should "encode to the same representation as a top-level enum" in {
    given codec: Codec[Paint] = stringRegistry.get(classOf[Paint])

    CodecTestKit.assertBsonStructure(
      Paint("wall", Colour.Green),
      BsonDocument.parse("""{"name": "wall", "colour": "Green"}""")
    )
  }

  it should "round-trip through encode and decode" in {
    given codec: Codec[Paint] = stringRegistry.get(classOf[Paint])

    CodecTestKit.roundTrip(Paint("wall", Colour.Green)) shouldBe Paint("wall", Colour.Green)
  }

  it should "decode a hand-written document that was never produced by encode" in {
    given codec: Codec[Paint] = stringRegistry.get(classOf[Paint])

    val golden = BsonDocument.parse("""{"name": "wall", "colour": "Green"}""")

    CodecTestKit.fromBsonDocument[Paint](golden) shouldBe Paint("wall", Colour.Green)
  }

  "An object-nested ordinal enum" should "encode to the same representation as a top-level enum" in {
    given codec: Codec[Paint] = ordinalRegistry.get(classOf[Paint])

    CodecTestKit.assertBsonStructure(
      Paint("wall", Colour.Green),
      BsonDocument.parse("""{"name": "wall", "colour": 1}""")
    )
  }

  it should "round-trip through encode and decode" in {
    given codec: Codec[Paint] = ordinalRegistry.get(classOf[Paint])

    CodecTestKit.roundTrip(Paint("wall", Colour.Green)) shouldBe Paint("wall", Colour.Green)
  }

  it should "decode a hand-written document that was never produced by encode" in {
    given codec: Codec[Paint] = ordinalRegistry.get(classOf[Paint])

    val golden = BsonDocument.parse("""{"name": "wall", "colour": 1}""")

    CodecTestKit.fromBsonDocument[Paint](golden) shouldBe Paint("wall", Colour.Green)
  }

  "A more deeply nested enum" should "round-trip when declared in an object inside an object" in {
    import Company.Domain.{Shade, Wall}

    val base = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(EnumValueCodecProvider.forStringEnum[Shade]),
      primitives
    )
    val registry = RegistryBuilder.from(base).register[Wall].build

    given codec: Codec[Wall] = registry.get(classOf[Wall])

    CodecTestKit.assertBsonStructure(
      Wall("north", Shade.Dark),
      BsonDocument.parse("""{"name": "north", "shade": "Dark"}""")
    )
    CodecTestKit.roundTrip(Wall("north", Shade.Dark)) shouldBe Wall("north", Shade.Dark)
  }

  // Declared inside the spec class itself - the shape that originally surfaced this bug.
  enum ClassColour:
    case Red, Green

  case class ClassPaint(name: String, colour: ClassColour)

  "An enum declared inside a class" should "round-trip when referenced from within that class" in {
    val base = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(EnumValueCodecProvider.forStringEnum[ClassColour]),
      primitives
    )
    val registry = RegistryBuilder.from(base).register[ClassPaint].build

    given codec: Codec[ClassPaint] = registry.get(classOf[ClassPaint])

    CodecTestKit.assertBsonStructure(
      ClassPaint("wall", ClassColour.Green),
      BsonDocument.parse("""{"name": "wall", "colour": "Green"}""")
    )
    CodecTestKit.roundTrip(ClassPaint("wall", ClassColour.Green)) shouldBe ClassPaint("wall", ClassColour.Green)
  }

  "An unknown enum value" should "keep its existing decode failure behavior" in {
    given codec: Codec[Paint] = stringRegistry.get(classOf[Paint])

    val golden = BsonDocument.parse("""{"name": "wall", "colour": "Purple"}""")

    val error = intercept[RuntimeException] {
      CodecTestKit.fromBsonDocument[Paint](golden)
    }

    error.getMessage should include("Purple")
  }
end NestedEnumSpec
