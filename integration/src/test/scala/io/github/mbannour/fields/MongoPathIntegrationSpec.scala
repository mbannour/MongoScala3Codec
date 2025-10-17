package io.github.mbannour.fields

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.mongodb.scala.bson.annotations.BsonProperty
import io.github.mbannour.fields.MongoPath.syntax.?

class MongoPathIntegrationSpec extends AnyFunSuite with Matchers:

  case class Address(street: String, @BsonProperty("zip") zipCode: Int)
  case class User(@BsonProperty("_id") id: String, address: Option[Address], name: String)

  test("resolves simple field names") {
    MongoPath.of[User](_.name) shouldBe "name"
  }

  test("respects @BsonProperty on top-level field") {
    MongoPath.of[User](_.id) shouldBe "_id"
  }

  test("supports transparent Option hop via .? syntax") {
    MongoPath.of[User](_.address.?.zipCode) shouldBe "address.zip"
  }
end MongoPathIntegrationSpec
