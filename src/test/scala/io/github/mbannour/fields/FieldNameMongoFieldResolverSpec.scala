package io.github.mbannour.fields

import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.funsuite.AnyFunSuite

class FieldNameMongoFieldResolverSpec extends AnyFunSuite {

  case class Address(city: String, zip: String)
  case class Owner(@BsonProperty("n")name: String, address: Address)
  case class Vehicle(id: String, owner: Owner)

  test("FieldNameExtractor extracts nested field names correctly") {
    val result = MongoFieldMapper[Vehicle].extract()

    val expected = List(
      "id" -> "id",
      "owner.n" -> "owner.n",
      "owner.address.city" -> "owner.address.city",
      "owner.address.zip" -> "owner.address.zip"
    )

    assert(result == expected)
  }
}
