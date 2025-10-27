package io.github.mbannour.bson.macros

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

sealed trait Animal
case class Dog(name: String) extends Animal
case class Cat(name: String) extends Animal

sealed trait Vehicle
case class Car(model: String) extends Vehicle
case class Bike(model: String) extends Vehicle
case class Truck(model: String) extends Vehicle

case class Standalone(name: String, truck: Truck)
class NonCaseClass

class CaseClassMapperSpec extends AnyFlatSpec with Matchers:

  import io.github.mbannour.bson.macros.CaseClassMapper.*

  "CaseClassMapper" should "map simple sealed hierarchies" in {
    val result = caseClassMap[Animal]
    result should contain theSameElementsAs Map(
      "Dog" -> classOf[Dog],
      "Cat" -> classOf[Cat]
    )
  }

  it should "map sealed hierarchies with multiple subclasses" in {
    val result = caseClassMap[Vehicle]
    result should contain theSameElementsAs Map(
      "Car" -> classOf[Car],
      "Bike" -> classOf[Bike],
      "Truck" -> classOf[Truck]
    )
  }

  it should "return the case class itself if no subclasses exist" in {
    val result = caseClassMap[Standalone]
    result should contain theSameElementsAs Map("Standalone" -> classOf[Standalone])
  }

  it should "fail for non-case classes" ignore {
    // FIXME this test has to be fixed
    assertThrows[Exception] {
      caseClassMap[NonCaseClass]
    }
  }

  it should "handle nested case classes" in {
    sealed trait Outer
    case class InnerA(value: String) extends Outer
    case class InnerB(value: Int) extends Outer

    val result = caseClassMap[Outer]
    result should contain theSameElementsAs Map(
      "InnerA" -> classOf[InnerA],
      "InnerB" -> classOf[InnerB]
    )
  }
end CaseClassMapperSpec
