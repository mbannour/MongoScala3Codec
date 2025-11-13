package io.github.mbannour.mongo.codecs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CodecConfigSpec extends AnyFlatSpec with Matchers:

  "CodecConfig" should "have correct default values" in {
    val config = CodecConfig()

    config.noneHandling shouldBe NoneHandling.Encode
    config.discriminatorField shouldBe "_type"
    config.discriminatorStrategy shouldBe DiscriminatorStrategy.SimpleName
  }

  it should "support custom noneHandling" in {
    val config = CodecConfig(noneHandling = NoneHandling.Ignore)

    config.noneHandling shouldBe NoneHandling.Ignore
    config.shouldEncodeNone shouldBe false
  }

  it should "support custom discriminatorField" in {
    val config = CodecConfig(discriminatorField = "_class")

    config.discriminatorField shouldBe "_class"
  }

  it should "support custom discriminatorStrategy" in {
    val customMapping: Map[Class[?], String] = Map(classOf[String] -> "STR")
    val config = CodecConfig(discriminatorStrategy = DiscriminatorStrategy.Custom(customMapping))

    config.discriminatorStrategy shouldBe DiscriminatorStrategy.Custom(customMapping)
  }

  "CodecConfig.shouldEncodeNone" should "return true when noneHandling is Encode" in {
    val config = CodecConfig(noneHandling = NoneHandling.Encode)

    config.shouldEncodeNone shouldBe true
  }

  it should "return false when noneHandling is Ignore" in {
    val config = CodecConfig(noneHandling = NoneHandling.Ignore)

    config.shouldEncodeNone shouldBe false
  }

  "CodecConfig.withIgnoreNone" should "return config with Ignore noneHandling" in {
    val config = CodecConfig()
    val updated = config.withIgnoreNone

    updated.noneHandling shouldBe NoneHandling.Ignore
    updated.discriminatorField shouldBe config.discriminatorField
    updated.discriminatorStrategy shouldBe config.discriminatorStrategy
  }

  "CodecConfig.withEncodeNone" should "return config with Encode noneHandling" in {
    val config = CodecConfig(noneHandling = NoneHandling.Ignore)
    val updated = config.withEncodeNone

    updated.noneHandling shouldBe NoneHandling.Encode
  }

  "CodecConfig.withDiscriminatorField" should "return config with custom discriminator field" in {
    val config = CodecConfig()
    val updated = config.withDiscriminatorField("_class")

    updated.discriminatorField shouldBe "_class"
    updated.noneHandling shouldBe config.noneHandling
    updated.discriminatorStrategy shouldBe config.discriminatorStrategy
  }

  "CodecConfig.withDiscriminatorStrategy" should "return config with custom strategy" in {
    val config = CodecConfig()
    val updated = config.withDiscriminatorStrategy(DiscriminatorStrategy.FullyQualifiedName)

    updated.discriminatorStrategy shouldBe DiscriminatorStrategy.FullyQualifiedName
    updated.noneHandling shouldBe config.noneHandling
    updated.discriminatorField shouldBe config.discriminatorField
  }

  "CodecConfig" should "support method chaining" in {
    val config = CodecConfig().withIgnoreNone
      .withDiscriminatorField("_class")
      .withDiscriminatorStrategy(DiscriminatorStrategy.FullyQualifiedName)

    config.noneHandling shouldBe NoneHandling.Ignore
    config.discriminatorField shouldBe "_class"
    config.discriminatorStrategy shouldBe DiscriminatorStrategy.FullyQualifiedName
  }

  "NoneHandling" should "have Encode case" in {
    NoneHandling.Encode shouldBe a[NoneHandling]
  }

  it should "have Ignore case" in {
    NoneHandling.Ignore shouldBe a[NoneHandling]
  }

  "DiscriminatorStrategy" should "support SimpleName" in {
    DiscriminatorStrategy.SimpleName shouldBe a[DiscriminatorStrategy]
  }

  it should "support FullyQualifiedName" in {
    DiscriminatorStrategy.FullyQualifiedName shouldBe a[DiscriminatorStrategy]
  }

  it should "support Custom with mapping" in {
    val mapping: Map[Class[?], String] = Map(classOf[String] -> "STRING")
    val strategy = DiscriminatorStrategy.Custom(mapping)

    strategy shouldBe a[DiscriminatorStrategy]
    strategy match
      case DiscriminatorStrategy.Custom(m) => m shouldBe mapping
      case _                               => fail("Expected Custom strategy")
  }

end CodecConfigSpec
