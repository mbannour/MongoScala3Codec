package io.github.mbannour.bson.macros

import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

case class Address1(@BsonProperty("c")city: String, zip: Int)
case class Person1(@BsonProperty("n")name: String, age: Int, address: Address1)
case class ComplexPerson(name: String, age: Option[Int], address: Address1, tags: List[String])
case class DefaultCase(name: String = "Unknown", age: Int = 0)
case class NestedCaseClass(fieldA: String, nested: Address1)
case class EnumCase(enumField: Color)

enum Color:
  case Red, Green, Blue

class CaseClassFactorySpec extends AnyFlatSpec with Matchers {

  "CaseClassFactory" should "instantiate a simple case class" in {
    val fieldData = Map("c" -> "New York", "zip" -> 10001)
    val address = CaseClassFactory.getInstance[Address1](fieldData)
    address should ===(Address1("New York", 10001))
  }

  it should "instantiate a nested case class" in {
    val fieldData = Map(
      "n" -> "John",
      "age" -> 30,
      "address" -> Map("c" -> "New York", "zip" -> 10001)
    )
    val person = CaseClassFactory.getInstance[Person1](fieldData)
    person should ===(Person1("John", 30, Address1("New York", 10001)))
  }

  it should "instantiate a case class with optional fields" in {
    val fieldData = Map(
      "name" -> "John",
      "address" -> Map("c" -> "Los Angeles", "zip" -> 90001),
      "tags" -> List("tag1", "tag2")
    )
    val complexPerson = CaseClassFactory.getInstance[ComplexPerson](fieldData)
    complexPerson should ===(ComplexPerson("John", None, Address1("Los Angeles", 90001), List("tag1", "tag2")))
  }


  it should "fail for unsupported types at compile time" in {
    """
    case class UnsupportedType(field: (String, Int))

    val fieldData = Map("field" -> ("a", 1))

     val exception = intercept[RuntimeException] {
      CaseClassFactory.getInstance[UnsupportedType](fieldData)
    }
    """ shouldNot compile
  }

  it should "throw an exception for missing required fields" in {
    val fieldData = Map("name" -> "John") // Missing "age" and "address"

    val exception = intercept[RuntimeException] {
      CaseClassFactory.getInstance[Person1](fieldData)
    }
    exception.getMessage should include("Missing field")
  }

  it should "throw an exception for invalid field data types" in {
    val fieldData = Map(
      "n" -> "John",
      "age" -> "thirty", // Invalid type
      "address" -> Map("c" -> "Chicago", "zip" -> 60601)
    )

    val exception = intercept[RuntimeException] {
      CaseClassFactory.getInstance[Person1](fieldData)
    }
    exception.getMessage should include("Error casting field age. Expected: scala.Int, Actual: java.lang.String")
  }


  it should "instantiate a case class with a Scala 3 enum field" in {
    val fieldData = Map("enumField" -> "Red")
    val enumCase = CaseClassFactory.getInstance[EnumCase](fieldData)
    enumCase should ===(EnumCase(Color.Red))
  }

  it should "throw an exception for invalid enum field values" in {
    val fieldData = Map("enumField" -> "InvalidColor")

    val exception = intercept[RuntimeException] {
      CaseClassFactory.getInstance[EnumCase](fieldData)
    }
    exception.getMessage should include("Error decoding enum field")
  }
}
