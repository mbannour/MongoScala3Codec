package io.github.mbannour.fields

import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import io.github.mbannour.fields.MongoPath.syntax.?

/** Comprehensive test suite for MongoPath.
  *
  * Tests cover:
  * - Simple field selection
  * - Nested field selection
  * - @BsonProperty annotation handling
  * - Option type handling (filters out .value)
  * - Multiple levels of nesting
  * - Complex scenarios with mixed annotations
  */
class MongoPathSpec extends AnyFunSuite with Matchers:

  // Test models
  case class SimpleModel(
      id: String,
      name: String,
      age: Int
  )

  case class WithAnnotations(
      @BsonProperty("user_id") id: String,
      @BsonProperty("n") name: String,
      email: String
  )

  case class Address(
      street: String,
      city: String,
      @BsonProperty("zip") zipCode: Int
  )

  case class NestedModel(
      id: String,
      name: String,
      address: Address
  )

  case class WithOptions(
      id: String,
      name: Option[String],
      age: Option[Int],
      address: Option[Address]
  )

  case class DeepNesting(
      level1: Level1
  )

  case class Level1(
      level2: Level2,
      @BsonProperty("l1_field") field1: String
  )

  case class Level2(
      level3: Level3,
      field2: String
  )

  case class Level3(
      finalField: String,
      @BsonProperty("final_custom") customField: String
  )

  case class MixedAnnotations(
      @BsonProperty("custom_id") id: String,
      normalField: String,
      nested: NestedWithAnnotation
  )

  case class NestedWithAnnotation(
      @BsonProperty("inner_id") id: String,
      name: String
  )

  // Simple field selection tests
  test("MongoPath.of should extract simple field name") {
    val path = MongoPath.of[SimpleModel](_.id)
    path shouldBe "id"
  }

  test("MongoPath.of should extract another simple field") {
    val path = MongoPath.of[SimpleModel](_.name)
    path shouldBe "name"
  }

  test("MongoPath.of should extract numeric field") {
    val path = MongoPath.of[SimpleModel](_.age)
    path shouldBe "age"
  }

  // @BsonProperty annotation tests
  test("MongoPath.of should use @BsonProperty annotation value") {
    val path = MongoPath.of[WithAnnotations](_.id)
    path shouldBe "user_id"
  }

  test("MongoPath.of should use @BsonProperty for name field") {
    val path = MongoPath.of[WithAnnotations](_.name)
    path shouldBe "n"
  }

  test("MongoPath.of should use field name when no annotation present") {
    val path = MongoPath.of[WithAnnotations](_.email)
    path shouldBe "email"
  }

  // Nested field selection tests
  test("MongoPath.of should extract nested field path") {
    val path = MongoPath.of[NestedModel](_.address.street)
    path shouldBe "address.street"
  }

  test("MongoPath.of should extract nested field with city") {
    val path = MongoPath.of[NestedModel](_.address.city)
    path shouldBe "address.city"
  }

  test("MongoPath.of should respect @BsonProperty in nested field") {
    val path = MongoPath.of[NestedModel](_.address.zipCode)
    path shouldBe "address.zip"
  }

  test("MongoPath.of should handle top-level field in model with nesting") {
    val path = MongoPath.of[NestedModel](_.id)
    path shouldBe "id"
  }

  // Option type tests - should filter out .value
  test("MongoPath.of should handle Option[String] field") {
    val path = MongoPath.of[WithOptions](_.name)
    path shouldBe "name"
  }

  test("MongoPath.of should handle Option[Int] field") {
    val path = MongoPath.of[WithOptions](_.age)
    path shouldBe "age"
  }

  test("MongoPath.of should handle Option[CaseClass] field") {
    val path = MongoPath.of[WithOptions](_.address)
    path shouldBe "address"
  }

  test("MongoPath.of should handle nested field in Option[CaseClass]") {
    val path = MongoPath.of[WithOptions](_.address.?.street)
    path shouldBe "address.street"
  }

  test("MongoPath.of should handle nested field with annotation in Option") {
    val path = MongoPath.of[WithOptions](_.address.?.zipCode)
    path shouldBe "address.zip"
  }

  // Deep nesting tests
  test("MongoPath.of should handle deeply nested field") {
    val path = MongoPath.of[DeepNesting](_.level1.level2.level3.finalField)
    path shouldBe "level1.level2.level3.finalField"
  }

  test("MongoPath.of should respect annotation in deep nesting") {
    val path = MongoPath.of[DeepNesting](_.level1.level2.level3.customField)
    path shouldBe "level1.level2.level3.final_custom"
  }

  test("MongoPath.of should handle annotation at first level of nesting") {
    val path = MongoPath.of[DeepNesting](_.level1.field1)
    path shouldBe "level1.l1_field"
  }

  test("MongoPath.of should handle middle level of deep nesting") {
    val path = MongoPath.of[DeepNesting](_.level1.level2.field2)
    path shouldBe "level1.level2.field2"
  }

  // Mixed annotations tests
  test("MongoPath.of should handle top-level annotation in mixed model") {
    val path = MongoPath.of[MixedAnnotations](_.id)
    path shouldBe "custom_id"
  }

  test("MongoPath.of should handle normal field in mixed model") {
    val path = MongoPath.of[MixedAnnotations](_.normalField)
    path shouldBe "normalField"
  }

  test("MongoPath.of should handle nested object in mixed model") {
    val path = MongoPath.of[MixedAnnotations](_.nested.name)
    path shouldBe "nested.name"
  }

  test("MongoPath.of should handle nested annotation in mixed model") {
    val path = MongoPath.of[MixedAnnotations](_.nested.id)
    path shouldBe "nested.inner_id"
  }

  // Edge cases
  test("MongoPath.of should handle single character field names") {
    case class SingleChar(a: String, b: Int)
    val pathA = MongoPath.of[SingleChar](_.a)
    val pathB = MongoPath.of[SingleChar](_.b)
    
    pathA shouldBe "a"
    pathB shouldBe "b"
  }

  test("MongoPath.of should handle underscore in field names") {
    case class WithUnderscore(field_name: String, another_field: Int)
    val path1 = MongoPath.of[WithUnderscore](_.field_name)
    val path2 = MongoPath.of[WithUnderscore](_.another_field)
    
    path1 shouldBe "field_name"
    path2 shouldBe "another_field"
  }

  test("MongoPath.of should handle camelCase field names") {
    case class CamelCase(firstName: String, lastName: String, emailAddress: String)
    val path1 = MongoPath.of[CamelCase](_.firstName)
    val path2 = MongoPath.of[CamelCase](_.lastName)
    val path3 = MongoPath.of[CamelCase](_.emailAddress)
    
    path1 shouldBe "firstName"
    path2 shouldBe "lastName"
    path3 shouldBe "emailAddress"
  }

  // Collections - these should work for the collection field itself
  test("MongoPath.of should handle List field") {
    case class WithList(items: List[String], count: Int)
    val path = MongoPath.of[WithList](_.items)
    path shouldBe "items"
  }

  test("MongoPath.of should handle Seq field") {
    case class WithSeq(tags: Seq[String])
    val path = MongoPath.of[WithSeq](_.tags)
    path shouldBe "tags"
  }

  test("MongoPath.of should handle Map field") {
    case class WithMap(properties: Map[String, String])
    val path = MongoPath.of[WithMap](_.properties)
    path shouldBe "properties"
  }

  // Annotation with different values
  test("MongoPath.of should handle annotation with underscores") {
    case class UnderscoreAnnotation(@BsonProperty("user_name") userName: String)
    val path = MongoPath.of[UnderscoreAnnotation](_.userName)
    path shouldBe "user_name"
  }

  test("MongoPath.of should handle annotation with dots") {
    case class DotAnnotation(@BsonProperty("user.name") userName: String)
    val path = MongoPath.of[DotAnnotation](_.userName)
    path shouldBe "user.name"
  }

  test("MongoPath.of should handle single letter annotation") {
    case class SingleLetterAnnotation(@BsonProperty("n") name: String)
    val path = MongoPath.of[SingleLetterAnnotation](_.name)
    path shouldBe "n"
  }

  // Combination tests
  test("MongoPath.of should handle complex combination of features") {
    case class Inner(@BsonProperty("i") id: String, value: String)
    case class Outer(
        @BsonProperty("_id") id: String,
        inner: Option[Inner],
        normalField: String
    )
    
    MongoPath.of[Outer](_.id) shouldBe "_id"
    MongoPath.of[Outer](_.normalField) shouldBe "normalField"
    MongoPath.of[Outer](_.inner) shouldBe "inner"
    MongoPath.of[Outer](_.inner.?.id) shouldBe "inner.i"
    MongoPath.of[Outer](_.inner.?.value) shouldBe "inner.value"
  }

  // Multiple Option nesting
  test("MongoPath.of should handle Option[Option[T]]") {
    case class DoubleOption(maybeValue: Option[Option[String]])
    val path = MongoPath.of[DoubleOption](_.maybeValue)
    path shouldBe "maybeValue"
  }

  // Realistic MongoDB scenarios
  test("MongoPath.of should handle MongoDB _id field") {
    case class Document(@BsonProperty("_id") id: String, data: String)
    val path = MongoPath.of[Document](_.id)
    path shouldBe "_id"
  }

  test("MongoPath.of should handle typical user model") {
    case class User(
        @BsonProperty("_id") id: String,
        @BsonProperty("user_name") username: String,
        email: String,
        profile: Option[UserProfile]
    )
    
    case class UserProfile(
        @BsonProperty("full_name") fullName: String,
        bio: Option[String]
    )
    
    MongoPath.of[User](_.id) shouldBe "_id"
    MongoPath.of[User](_.username) shouldBe "user_name"
    MongoPath.of[User](_.email) shouldBe "email"
    MongoPath.of[User](_.profile) shouldBe "profile"
    MongoPath.of[User](_.profile.?.fullName) shouldBe "profile.full_name"
    MongoPath.of[User](_.profile.?.bio) shouldBe "profile.bio"
  }

end MongoPathSpec
