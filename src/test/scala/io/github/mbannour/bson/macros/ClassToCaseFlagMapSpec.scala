package io.github.mbannour.bson.macros

import scala.reflect.ClassTag

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ClassToCaseFlagMapSpec extends AnyFlatSpec with Matchers:

  case class Car(brand: String, model: String, engine: Engine)

  case class Engine(horsepower: Int, fuelType: String)

  sealed trait Animal

  case class Dog(name: String) extends Animal

  class NonCaseClass(val data: String)

  "classToCaseClassMap" should "handle primitive types" in {
    val result = ClassToCaseFlagMap.classToCaseClassMap[Int]
    result should contain key classOf[Int]
    result(classOf[Int]) shouldBe false
  }

  it should "identify case classes" in {
    val result = ClassToCaseFlagMap.classToCaseClassMap[Car]
    result should contain key classOf[Car]
    result(classOf[Car]) shouldBe true
  }

  it should "identify sealed classes" in {
    val result = ClassToCaseFlagMap.classToCaseClassMap[Animal]
    result should contain key classOf[Animal]
    result(classOf[Animal]) shouldBe true
  }

  it should "handle nested case classes" in {
    val result = ClassToCaseFlagMap.classToCaseClassMap[Car]
    result should contain key classOf[Car]
    result(classOf[Car]) shouldBe true
    result should contain key classOf[Engine]
    result(classOf[Engine]) shouldBe true
    result should contain key classOf[java.lang.String]
    result(classOf[java.lang.String]) shouldBe false
  }

  it should "not flag non-case classes as case classes" in {
    val result = ClassToCaseFlagMap.classToCaseClassMap[NonCaseClass]
    result should contain key classOf[NonCaseClass]
    result(classOf[NonCaseClass]) shouldBe false
  }

  it should "handle mixed types" in {
    val result = ClassToCaseFlagMap.classToCaseClassMap[NonCaseClass]
    result should contain key classOf[NonCaseClass]
    result(classOf[NonCaseClass]) shouldBe false
  }
end ClassToCaseFlagMapSpec
