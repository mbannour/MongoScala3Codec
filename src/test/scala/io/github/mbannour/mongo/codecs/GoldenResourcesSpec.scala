package io.github.mbannour.mongo.codecs

import scala.io.Source

import org.bson.BsonDocument
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder._

/** Golden tests using JSON resources to assert exact BSON encoding/decoding equivalence. */
class GoldenResourcesSpec extends AnyFlatSpec with Matchers:

  private def loadBson(resourcePath: String): BsonDocument =
    val json = Source.fromResource(resourcePath).mkString
    BsonDocument.parse(json)

  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  // Models for goldens
  case class SimpleUser(_id: ObjectId, name: String, age: Int)

  case class Address(_id: ObjectId, street: String, city: String, zipCode: Int)
  case class UserWithNested(_id: ObjectId, name: String, address: Address)

  case class UserWithCollections(_id: ObjectId, tags: List[String], scores: Seq[Int])

  case class Circle(_id: ObjectId, radius: Double, shapeType: String = "Circle")

  private val registry = RegistryBuilder
    .from(defaultBsonRegistry)
    .register[Address]
    .register[UserWithNested]
    .register[SimpleUser]
    .register[UserWithCollections]
    .register[Circle]
    .build

  "SimpleUser golden" should "match resource BSON exactly and decode back" in {
    given Codec[SimpleUser] = registry.get(classOf[SimpleUser])

    val expected = loadBson("golden/simple_user.json")
    val user = SimpleUser(new ObjectId("507f1f77bcf86cd799439011"), "Alice", 30)

    // encode equality
    CodecTestKit.assertBsonStructure(user, expected)

    // decode equality
    val decoded = CodecTestKit.fromBsonDocument[SimpleUser](expected)
    decoded shouldBe user
  }

  "UserWithNested golden" should "match nested structure from resource" in {
    given Codec[UserWithNested] = registry.get(classOf[UserWithNested])

    val addressId = new ObjectId("507f1f77bcf86cd799439012")
    val userId = new ObjectId("507f1f77bcf86cd799439013")

    val expected = loadBson("golden/user_with_nested.json")
    val model = UserWithNested(userId, "Diana", Address(addressId, "123 Main St", "Springfield", 12345))

    CodecTestKit.assertBsonStructure(model, expected)
    CodecTestKit.fromBsonDocument[UserWithNested](expected) shouldBe model
  }

  "UserWithCollections golden" should "match arrays from resource" in {
    given Codec[UserWithCollections] = registry.get(classOf[UserWithCollections])

    val id = new ObjectId("507f1f77bcf86cd799439014")
    val expected = loadBson("golden/user_with_collections.json")
    val model = UserWithCollections(id, List("scala", "mongodb", "functional"), Seq(100, 95, 88))

    CodecTestKit.assertBsonStructure(model, expected)
    CodecTestKit.fromBsonDocument[UserWithCollections](expected) shouldBe model
  }

  "Circle golden" should "match resource with discriminator field" in {
    given Codec[Circle] = registry.get(classOf[Circle])

    val id = new ObjectId("507f1f77bcf86cd799439015")
    val expected = loadBson("golden/circle.json")
    val model = Circle(id, 5.0)

    CodecTestKit.assertBsonStructure(model, expected)
    CodecTestKit.fromBsonDocument[Circle](expected) shouldBe model
  }
end GoldenResourcesSpec
