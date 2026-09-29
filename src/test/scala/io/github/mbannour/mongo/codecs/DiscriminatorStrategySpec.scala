package io.github.mbannour.mongo.codecs

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonDiscriminator
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

sealed trait Payment
case class Card(last4: String) extends Payment
case class Cash(note: String) extends Payment

sealed trait Settlement
@BsonDiscriminator("wire") case class BankWire(iban: String) extends Settlement
case class Cheque(number: String) extends Settlement

object Nested:
  sealed trait Inner
  case class Leaf(name: String) extends Inner

/** `CodecConfig.discriminatorStrategy` decides the value a sealed subtype is stored under.
  *
  * Up to 1.0 this setting was inert: it was stored, had a setter and had tests asserting the setter worked, but no generator read it, so
  * every strategy produced the simple name. These tests assert the stored document, which is the only thing that can tell the strategies
  * apart.
  */
class DiscriminatorStrategySpec extends AnyFlatSpec with Matchers:

  private val primitives = CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())

  private def registryFor(strategy: DiscriminatorStrategy): CodecRegistry =
    RegistryBuilder
      .from(primitives)
      .configure(_.withDiscriminatorStrategy(strategy))
      .registerSealed[Payment]
      .build

  private def discriminatorOf(strategy: DiscriminatorStrategy, value: Payment): String =
    val codec = registryFor(strategy).get(classOf[Payment])
    CodecTestKit(codec).encode(value).getString("_type").getValue

  "SimpleName" should "store a subtype under its simple name" in {
    discriminatorOf(DiscriminatorStrategy.SimpleName, Card("4242")) shouldBe "Card"
  }

  "FullyQualifiedName" should "store a subtype under its fully qualified name" in {
    discriminatorOf(DiscriminatorStrategy.FullyQualifiedName, Card("4242")) shouldBe
      "io.github.mbannour.mongo.codecs.Card"
  }

  "Custom" should "store a subtype under its mapped value" in {
    val strategy = DiscriminatorStrategy.Custom(Map(classOf[Card] -> "C", classOf[Cash] -> "K"))

    discriminatorOf(strategy, Card("4242")) shouldBe "C"
    discriminatorOf(strategy, Cash("tip")) shouldBe "K"
  }

  it should "leave an unmapped subtype at its simple name" in {
    val strategy = DiscriminatorStrategy.Custom(Map(classOf[Card] -> "C"))

    discriminatorOf(strategy, Cash("tip")) shouldBe "Cash"
  }

  "Every strategy" should "round-trip every subtype it renamed" in {
    val strategies = List(
      DiscriminatorStrategy.SimpleName,
      DiscriminatorStrategy.FullyQualifiedName,
      DiscriminatorStrategy.Custom(Map(classOf[Card] -> "C", classOf[Cash] -> "K"))
    )

    strategies.foreach { strategy =>
      val kit = CodecTestKit(registryFor(strategy).get(classOf[Payment]))
      kit.assertRoundTrip(Card("4242"))
      kit.assertRoundTrip(Cash("tip"))
    }
  }

  "@BsonDiscriminator" should "outrank the strategy" in {
    val registry = RegistryBuilder
      .from(primitives)
      .configure(_.withDiscriminatorStrategy(DiscriminatorStrategy.FullyQualifiedName))
      .registerSealed[Settlement]
      .build
    val kit = CodecTestKit(registry.get(classOf[Settlement]))

    // Annotated: the explicit value wins over the strategy.
    kit.encode(BankWire("DE00")).getString("_type").getValue shouldBe "wire"
    // Unannotated sibling in the same hierarchy: the strategy still applies.
    kit.encode(Cheque("42")).getString("_type").getValue shouldBe
      "io.github.mbannour.mongo.codecs.Cheque"

    kit.assertRoundTrip(BankWire("DE00"))
    kit.assertRoundTrip(Cheque("42"))
  }

  "A nested subtype under FullyQualifiedName" should "read as a dotted path, not a synthetic one" in {
    val registry = RegistryBuilder
      .from(primitives)
      .configure(_.withDiscriminatorStrategy(DiscriminatorStrategy.FullyQualifiedName))
      .registerSealed[Nested.Inner]
      .build
    val kit = CodecTestKit(registry.get(classOf[Nested.Inner]))
    val stored = kit.encode(Nested.Leaf("x")).getString("_type").getValue

    stored should not include "$"
    stored shouldBe "io.github.mbannour.mongo.codecs.Nested.Leaf"
    kit.assertRoundTrip(Nested.Leaf("x"))
  }

  "A Custom mapping that collides" should "fail while the registry is built, not when a document is written" in {
    val collide = DiscriminatorStrategy.Custom(Map(classOf[Card] -> "X", classOf[Cash] -> "X"))
    val thrown = intercept[IllegalArgumentException](registryFor(collide).get(classOf[Payment]))

    thrown.getMessage should include("resolved 'X' for 2 subtypes")
  }

  "A Custom mapping with an empty value" should "be rejected" in {
    val empty = DiscriminatorStrategy.Custom(Map(classOf[Card] -> ""))
    val thrown = intercept[IllegalArgumentException](registryFor(empty).get(classOf[Payment]))

    thrown.getMessage should include("empty value")
  }
end DiscriminatorStrategySpec
