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

enum Status(val code: Int):
  case Ok extends Status(200)
  case NotFound extends Status(404)

case class StatusCase(status: Status)

enum Level:
  case Low, High

case class Inner(level: Level)

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
    assertDoesNotCompile("""
      case class UnsupportedType(field: (String, Int))
      val fieldData = Map("field" -> ("a", 1))
      CaseClassFactory.getInstance[UnsupportedType](fieldData)
    """)
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

  it should "instantiate a case class with all field types" in {
    case class AllTypes(a: Int, b: String, c: Boolean, d: Double, e: List[String], f: Option[Int])
    val fieldData = Map(
      "a" -> 42,
      "b" -> "hello",
      "c" -> true,
      "d" -> 3.14,
      "e" -> List("x", "y"),
      "f" -> 1
    )
    val result = CaseClassFactory.getInstance[AllTypes](fieldData)
    result should ===(AllTypes(42, "hello", true, 3.14, List("x", "y"), Some(1)))
  }

  it should "instantiate a case class with a nested enum field" in {
    val fieldData = Map("level" -> "High")
    val result = CaseClassFactory.getInstance[Inner](fieldData)
    result should ===(Inner(Level.High))
  }

  it should "return a Inner(null)" in {
    val fieldData = Map("level" -> null)
    val result = CaseClassFactory.getInstance[Inner](fieldData)
    result should be(Inner(null))
  }

  it should "instantiate a case class with an enum field using ordinal value" in {
    val fieldData = Map("enumField" -> 2)
    val result = CaseClassFactory.getInstance[EnumCase](fieldData)
    result should ===(EnumCase(Color.Blue))
  }

  it should "instantiate a case class with a custom enum codec using parameter field" in {

    val fieldData = Map("status" -> 200)
    val result = CaseClassFactory.getInstance[StatusCase](fieldData)
    result.status.code should ===(200)
    result.status should ===(Status.Ok)
  }

}
