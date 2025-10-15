package io.github.mbannour.bson.macros

import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.should.Matchers.*

import scala.reflect.ClassTag

case class Address(city: String, zip: Int)

case class Person(@BsonProperty("n") name: String, age: Int, address: Address)

case class Complex(
    id: Long,
    isActive: Boolean,
    tags: List[String],
    nested: Option[Address]
)

class CaseClassFieldMapperSpec extends AnyFlatSpec with Matchers with TypeCheckedTripleEquals:

  "CaseClassFieldMapper" should "map case class fields correctly" in {
    val result = CaseClassFieldMapper.createClassFieldTypeArgsMap[Person]

    result.keys should ===(Set("Person", "Address"))
    result("Person") should ===(
      Map(
        "n" -> List(classOf[String]),
        "age" -> List(classOf[Int]),
        "address" -> List(classOf[Address])
      )
    )
    result("Address") should ===(
      Map(
        "city" -> List(classOf[String]),
        "zip" -> List(classOf[Int])
      )
    )
  }

  it should "handle nested case classes and primitives correctly" in {
    val result = CaseClassFieldMapper.createClassFieldTypeArgsMap[Complex]

    result.keys should ===(Set("Complex"))
    result("Complex") should ===(
      Map(
        "id" -> List(classOf[Long]),
        "isActive" -> List(classOf[Boolean]),
        "tags" -> List(classOf[List[?]], classOf[String]),
        "nested" -> List(classOf[Address])
      )
    )
  }

  it should "fail for unsupported types at compile time" in {
    """
      case class Unsupported(tuple: (String, Int))
      CaseClassFieldMapper.createClassFieldTypeArgsMap[Unsupported]
    """ shouldNot compile
  }
end CaseClassFieldMapperSpec
