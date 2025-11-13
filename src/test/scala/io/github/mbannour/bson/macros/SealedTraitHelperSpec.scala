package io.github.mbannour.bson.macros

import io.github.mbannour.mongo.codecs.DiscriminatorStrategy
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SealedTraitHelperSpec extends AnyFlatSpec with Matchers:

  sealed trait Color
  case class Red() extends Color
  case class Green() extends Color
  case class Blue() extends Color

  sealed trait Shape
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  case class Triangle(base: Double, height: Double) extends Shape

  case class RegularClass(value: String)

  "SealedTraitHelper.isSealedTrait" should "return true for sealed traits" in {
    SealedTraitHelper.isSealedTrait[Color] shouldBe true
    SealedTraitHelper.isSealedTrait[Shape] shouldBe true
  }

  it should "return false for non-sealed types" in {
    SealedTraitHelper.isSealedTrait[RegularClass] shouldBe false
    SealedTraitHelper.isSealedTrait[String] shouldBe false
    SealedTraitHelper.isSealedTrait[Int] shouldBe false
  }

  "SealedTraitHelper.createDiscriminatorMap" should "create map with SimpleName strategy" in {
    val map = SealedTraitHelper.createDiscriminatorMap[Color](DiscriminatorStrategy.SimpleName)

    map.keys should contain allOf ("Red", "Green", "Blue")
    map("Red") shouldBe classOf[Red]
    map("Green") shouldBe classOf[Green]
    map("Blue") shouldBe classOf[Blue]
  }

  it should "create map with FullyQualifiedName strategy" in {
    val map = SealedTraitHelper.createDiscriminatorMap[Shape](DiscriminatorStrategy.FullyQualifiedName)

    map.keys.foreach { key =>
      key should include("io.github.mbannour.bson.macros.SealedTraitHelperSpec")
    }

    map.values should contain allOf (classOf[Circle], classOf[Rectangle], classOf[Triangle])
  }

  it should "create map with Custom strategy" in {
    val customMapping: Map[Class[?], String] = Map(
      classOf[Red] -> "RED_COLOR",
      classOf[Green] -> "GREEN_COLOR",
      classOf[Blue] -> "BLUE_COLOR"
    )

    val map = SealedTraitHelper.createDiscriminatorMap[Color](DiscriminatorStrategy.Custom(customMapping))

    map should contain("RED_COLOR" -> classOf[Red])
    map should contain("GREEN_COLOR" -> classOf[Green])
    map should contain("BLUE_COLOR" -> classOf[Blue])
  }

  it should "use simple name as fallback in Custom strategy" in {
    val partialMapping: Map[Class[?], String] = Map(
      classOf[Red] -> "RED_CUSTOM"
    )

    val map = SealedTraitHelper.createDiscriminatorMap[Color](DiscriminatorStrategy.Custom(partialMapping))

    map should contain("RED_CUSTOM" -> classOf[Red])
    // Green and Blue should fall back to simple names
    map should contain("Green" -> classOf[Green])
    map should contain("Blue" -> classOf[Blue])
  }

  "SealedTraitHelper.createReverseDiscriminatorMap" should "create reverse map with SimpleName strategy" in {
    val map = SealedTraitHelper.createReverseDiscriminatorMap[Color](DiscriminatorStrategy.SimpleName)

    map(classOf[Red]) shouldBe "Red"
    map(classOf[Green]) shouldBe "Green"
    map(classOf[Blue]) shouldBe "Blue"
  }

  it should "create reverse map with FullyQualifiedName strategy" in {
    val map = SealedTraitHelper.createReverseDiscriminatorMap[Shape](DiscriminatorStrategy.FullyQualifiedName)

    map(classOf[Circle]) should include("Circle")
    map(classOf[Rectangle]) should include("Rectangle")
    map(classOf[Triangle]) should include("Triangle")

    map.values.foreach { value =>
      value should include("io.github.mbannour.bson.macros.SealedTraitHelperSpec")
    }
  }

  it should "create reverse map with Custom strategy" in {
    val customMapping: Map[Class[?], String] = Map(
      classOf[Circle] -> "CIRCLE_SHAPE",
      classOf[Rectangle] -> "RECT_SHAPE",
      classOf[Triangle] -> "TRI_SHAPE"
    )

    val map = SealedTraitHelper.createReverseDiscriminatorMap[Shape](DiscriminatorStrategy.Custom(customMapping))

    map(classOf[Circle]) shouldBe "CIRCLE_SHAPE"
    map(classOf[Rectangle]) shouldBe "RECT_SHAPE"
    map(classOf[Triangle]) shouldBe "TRI_SHAPE"
  }

  "SealedTraitHelper.getDiscriminatorValue" should "return simple name for SimpleName strategy" in {
    val value = SealedTraitHelper.getDiscriminatorValue(classOf[Circle], DiscriminatorStrategy.SimpleName)
    value shouldBe "Circle"
  }

  it should "return fully qualified name for FullyQualifiedName strategy" in {
    val value = SealedTraitHelper.getDiscriminatorValue(classOf[Rectangle], DiscriminatorStrategy.FullyQualifiedName)
    value should include("SealedTraitHelperSpec")
    value should include("Rectangle")
  }

  it should "return custom value for Custom strategy" in {
    val customMapping: Map[Class[?], String] = Map(
      classOf[Triangle] -> "CUSTOM_TRIANGLE"
    )
    val value = SealedTraitHelper.getDiscriminatorValue(classOf[Triangle], DiscriminatorStrategy.Custom(customMapping))
    value shouldBe "CUSTOM_TRIANGLE"
  }

  it should "return simple name as fallback for Custom strategy when class not in mapping" in {
    val customMapping: Map[Class[?], String] = Map(
      classOf[Circle] -> "CIRCLE_CUSTOM"
    )
    val value = SealedTraitHelper.getDiscriminatorValue(classOf[Rectangle], DiscriminatorStrategy.Custom(customMapping))
    value shouldBe "Rectangle"
  }

  "SealedTraitHelper discriminator maps" should "be consistent" in {
    val forwardMap = SealedTraitHelper.createDiscriminatorMap[Color](DiscriminatorStrategy.SimpleName)
    val reverseMap = SealedTraitHelper.createReverseDiscriminatorMap[Color](DiscriminatorStrategy.SimpleName)

    // Forward and reverse maps should be inverses of each other
    forwardMap.foreach { case (discriminator, clazz) =>
      reverseMap(clazz) shouldBe discriminator
    }

    reverseMap.foreach { case (clazz, discriminator) =>
      forwardMap(discriminator) shouldBe clazz
    }
  }

  it should "handle all subtypes" in {
    val forwardMap = SealedTraitHelper.createDiscriminatorMap[Shape](DiscriminatorStrategy.SimpleName)
    val reverseMap = SealedTraitHelper.createReverseDiscriminatorMap[Shape](DiscriminatorStrategy.SimpleName)

    // Should have entries for all three subtypes
    forwardMap should have size 3
    reverseMap should have size 3
  }

end SealedTraitHelperSpec
