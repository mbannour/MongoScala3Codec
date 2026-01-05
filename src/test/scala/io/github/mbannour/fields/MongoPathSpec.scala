package io.github.mbannour.fields

import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.fields.MongoPath.syntax.{?, each}

/** Comprehensive test suite for MongoPath.
  *
  * Tests cover:
  *   - Simple field selection
  *   - Nested field selection
  *   - \@BsonProperty annotation handling
  *   - Option type handling (filters out .value)
  *   - Multiple levels of nesting
  *   - Complex scenarios with mixed annotations
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

  // Seq/List array field navigation tests
  test("MongoPath.of should handle Seq field navigation with .each") {
    case class Skill(name: String, level: String)
    case class Employee(skills: Seq[Skill])

    val path = MongoPath.of[Employee](_.skills.each.name)
    path shouldBe "skills.name"
  }

  test("MongoPath.of should handle List field navigation with .each") {
    case class Tag(value: String, category: String)
    case class Article(tags: List[Tag])

    val path = MongoPath.of[Article](_.tags.each.value)
    path shouldBe "tags.value"
  }

  test("MongoPath.of should handle nested Seq field with @BsonProperty") {
    case class Skill(@BsonProperty("skill_name") name: String, level: String)
    case class Employee(skills: Seq[Skill])

    val path = MongoPath.of[Employee](_.skills.each.name)
    path shouldBe "skills.skill_name"
  }

  test("MongoPath.of should handle multiple levels with Seq navigation") {
    case class Project(name: String, budget: Double)
    case class Department(projects: Seq[Project])
    case class Company(departments: List[Department])

    MongoPath.of[Company](_.departments.each.projects.each.name) shouldBe "departments.projects.name"
  }

  test("MongoPath.of should handle Seq with nested objects and annotations") {
    case class Address(@BsonProperty("zip") zipCode: Int, city: String)
    case class Contact(addresses: Seq[Address])

    MongoPath.of[Contact](_.addresses.each.zipCode) shouldBe "addresses.zip"
    MongoPath.of[Contact](_.addresses.each.city) shouldBe "addresses.city"
  }

  // Note: Chaining .? and .each (Option[Seq]) is an edge case
  // For MongoDB queries, if skills is Option[Seq[Skill]], you can still use just .each or access skills directly
  // since MongoDB handles optional fields automatically in queries
  test("MongoPath.of should handle Option[Seq] field directly") {
    case class Skill(name: String, level: String)
    case class Employee(skills: Option[Seq[Skill]])

    val path = MongoPath.of[Employee](_.skills)
    path shouldBe "skills"
  }

  test("MongoPath.of should handle Seq[Option] with .each and .?") {
    case class Tag(value: Option[String])
    case class Article(tags: Seq[Tag])

    val path = MongoPath.of[Article](_.tags.each.value)
    path shouldBe "tags.value"
  }

  test("MongoPath.of should handle real-world Employee example") {
    case class Skill(
        name: String,
        level: String,
        yearsOfExperience: Int
    )
    case class Project(
        @BsonProperty("project_name") name: String,
        role: String,
        @BsonProperty("start_date") startDate: String
    )
    case class Employee(
        id: String,
        name: String,
        skills: Seq[Skill],
        projects: Seq[Project]
    )

    MongoPath.of[Employee](_.skills.each.name) shouldBe "skills.name"
    MongoPath.of[Employee](_.skills.each.level) shouldBe "skills.level"
    MongoPath.of[Employee](_.skills.each.yearsOfExperience) shouldBe "skills.yearsOfExperience"
    MongoPath.of[Employee](_.projects.each.name) shouldBe "projects.project_name"
    MongoPath.of[Employee](_.projects.each.role) shouldBe "projects.role"
    MongoPath.of[Employee](_.projects.each.startDate) shouldBe "projects.start_date"
  }

  test("MongoPath.of should handle deeply nested Seq fields") {
    case class Certification(name: String, year: Int)
    case class Skill(name: String, certifications: Seq[Certification])
    case class Employee(skills: Seq[Skill])

    val path = MongoPath.of[Employee](_.skills.each.certifications.each.name)
    path shouldBe "skills.certifications.name"
  }

  test("MongoPath.of should handle mixed nested objects and Seq") {
    case class Skill(name: String)
    case class Department(name: String)
    case class Employee(
        department: Department,
        skills: Seq[Skill]
    )

    MongoPath.of[Employee](_.department.name) shouldBe "department.name"
    MongoPath.of[Employee](_.skills.each.name) shouldBe "skills.name"
  }

  // Seq/List of primitives tests
  test("MongoPath.of should handle Seq[String] field") {
    case class Document(tags: Seq[String])
    val path = MongoPath.of[Document](_.tags)
    path shouldBe "tags"
  }

  test("MongoPath.of should handle Seq[Int] field") {
    case class Numbers(values: Seq[Int])
    val path = MongoPath.of[Numbers](_.values)
    path shouldBe "values"
  }

  test("MongoPath.of should handle Seq[Double] field") {
    case class Metrics(scores: Seq[Double])
    val path = MongoPath.of[Metrics](_.scores)
    path shouldBe "scores"
  }

  test("MongoPath.of should handle List[String] field") {
    case class Document(keywords: List[String])
    val path = MongoPath.of[Document](_.keywords)
    path shouldBe "keywords"
  }

  test("MongoPath.of should handle Option[Seq[String]] field") {
    case class Document(tags: Option[Seq[String]])
    val path = MongoPath.of[Document](_.tags)
    path shouldBe "tags"
  }

  test("MongoPath.of should handle Option[List[Int]] field") {
    case class Numbers(values: Option[List[Int]])
    val path = MongoPath.of[Numbers](_.values)
    path shouldBe "values"
  }

  // BsonProperty on Seq fields themselves
  test("MongoPath.of should respect @BsonProperty on Seq field") {
    case class Skill(name: String)
    case class Employee(@BsonProperty("skill_set") skills: Seq[Skill])

    MongoPath.of[Employee](_.skills) shouldBe "skill_set"
  }

  test("MongoPath.of should respect @BsonProperty on Seq field and navigate with .each") {
    case class Skill(name: String, level: String)
    case class Employee(@BsonProperty("skill_set") skills: Seq[Skill])

    MongoPath.of[Employee](_.skills.each.name) shouldBe "skill_set.name"
  }

  test("MongoPath.of should handle @BsonProperty on both Seq field and nested field") {
    case class Skill(@BsonProperty("skill_name") name: String, level: String)
    case class Employee(@BsonProperty("skill_set") skills: Seq[Skill])

    MongoPath.of[Employee](_.skills.each.name) shouldBe "skill_set.skill_name"
    MongoPath.of[Employee](_.skills.each.level) shouldBe "skill_set.level"
  }

  // Vector support
  test("MongoPath.of should handle Vector[T] field") {
    case class Skill(name: String)
    case class Employee(skills: Vector[Skill])

    val path = MongoPath.of[Employee](_.skills)
    path shouldBe "skills"
  }

  test("MongoPath.of should handle Vector[T] field navigation with .each") {
    case class Skill(name: String, level: String)
    case class Employee(skills: Vector[Skill])

    MongoPath.of[Employee](_.skills.each.name) shouldBe "skills.name"
    MongoPath.of[Employee](_.skills.each.level) shouldBe "skills.level"
  }

  test("MongoPath.of should handle IndexedSeq[T] field") {
    case class Tag(value: String)
    case class Document(tags: IndexedSeq[Tag])

    val path = MongoPath.of[Document](_.tags)
    path shouldBe "tags"
  }

  test("MongoPath.of should handle IndexedSeq[T] field navigation with .each") {
    case class Tag(value: String, category: String)
    case class Document(tags: IndexedSeq[Tag])

    MongoPath.of[Document](_.tags.each.value) shouldBe "tags.value"
    MongoPath.of[Document](_.tags.each.category) shouldBe "tags.category"
  }

  test("MongoPath.of should handle Iterable[T] field") {
    case class Item(name: String)
    case class Collection(items: Iterable[Item])

    val path = MongoPath.of[Collection](_.items)
    path shouldBe "items"
  }

  test("MongoPath.of should handle Iterable[T] field navigation with .each") {
    case class Item(name: String, price: Double)
    case class Collection(items: Iterable[Item])

    MongoPath.of[Collection](_.items.each.name) shouldBe "items.name"
    MongoPath.of[Collection](_.items.each.price) shouldBe "items.price"
  }

  // Complex real-world Employee model
  test("MongoPath.of should handle complete Employee model") {
    case class Skill(name: String, level: String, yearsOfExperience: Int)
    case class Project(name: String, role: String)
    case class Address(street: String, city: String, zipCode: String)
    case class Contact(email: String, phone: String)
    case class Employee(
        id: String,
        name: String,
        position: String,
        salary: Double,
        department: Option[String],
        age: Int,
        hireDate: String,
        isActive: Boolean,
        address: Address,
        contact: Option[Contact],
        skills: Seq[Skill],
        projects: Seq[Project],
        certifications: Option[Seq[String]],
        previousSalaries: Seq[Double],
        performanceRating: Option[Double]
    )

    // Simple fields
    MongoPath.of[Employee](_.id) shouldBe "id"
    MongoPath.of[Employee](_.name) shouldBe "name"
    MongoPath.of[Employee](_.position) shouldBe "position"
    MongoPath.of[Employee](_.salary) shouldBe "salary"
    MongoPath.of[Employee](_.age) shouldBe "age"
    MongoPath.of[Employee](_.hireDate) shouldBe "hireDate"
    MongoPath.of[Employee](_.isActive) shouldBe "isActive"

    // Optional fields
    MongoPath.of[Employee](_.department) shouldBe "department"
    MongoPath.of[Employee](_.performanceRating) shouldBe "performanceRating"

    // Nested object
    MongoPath.of[Employee](_.address.street) shouldBe "address.street"
    MongoPath.of[Employee](_.address.city) shouldBe "address.city"
    MongoPath.of[Employee](_.address.zipCode) shouldBe "address.zipCode"

    // Optional nested object
    MongoPath.of[Employee](_.contact.?.email) shouldBe "contact.email"
    MongoPath.of[Employee](_.contact.?.phone) shouldBe "contact.phone"

    // Seq of objects with .each
    MongoPath.of[Employee](_.skills.each.name) shouldBe "skills.name"
    MongoPath.of[Employee](_.skills.each.level) shouldBe "skills.level"
    MongoPath.of[Employee](_.skills.each.yearsOfExperience) shouldBe "skills.yearsOfExperience"

    MongoPath.of[Employee](_.projects.each.name) shouldBe "projects.name"
    MongoPath.of[Employee](_.projects.each.role) shouldBe "projects.role"

    // Seq of primitives (no .each needed)
    MongoPath.of[Employee](_.previousSalaries) shouldBe "previousSalaries"

    // Option[Seq] of primitives
    MongoPath.of[Employee](_.certifications) shouldBe "certifications"
  }

  // Edge case: deeply nested with Option and Seq combinations
  test("MongoPath.of should handle deep nesting with Option and Seq") {
    case class Tag(name: String)
    case class Category(@BsonProperty("tag_list") tags: Seq[Tag])
    case class Section(categories: Option[Seq[Category]])
    case class Document(sections: Seq[Section])

    MongoPath.of[Document](_.sections.each.categories) shouldBe "sections.categories"
  }

  test("MongoPath.of should handle Seq inside Option inside Seq") {
    case class Item(value: String)
    case class Group(items: Option[Seq[Item]])
    case class Container(groups: Seq[Group])

    MongoPath.of[Container](_.groups.each.items) shouldBe "groups.items"
  }

  // @BsonProperty with special MongoDB field names
  test("MongoPath.of should handle @BsonProperty with MongoDB _id convention") {
    case class Record(@BsonProperty("_id") id: String, data: String)
    case class Collection(records: Seq[Record])

    MongoPath.of[Collection](_.records.each.id) shouldBe "records._id"
  }

  // Multiple Seq fields in same class
  test("MongoPath.of should handle multiple Seq fields") {
    case class Skill(name: String)
    case class Project(name: String)
    case class Certification(name: String)
    case class Employee(
        skills: Seq[Skill],
        projects: Seq[Project],
        certifications: Seq[Certification]
    )

    MongoPath.of[Employee](_.skills.each.name) shouldBe "skills.name"
    MongoPath.of[Employee](_.projects.each.name) shouldBe "projects.name"
    MongoPath.of[Employee](_.certifications.each.name) shouldBe "certifications.name"
  }

  // Seq field at different nesting levels
  test("MongoPath.of should handle Seq at different nesting levels") {
    case class Tag(value: String)
    case class Inner(tags: Seq[Tag])
    case class Middle(inner: Inner, moreTags: Seq[Tag])
    case class Outer(middle: Middle, evenMoreTags: Seq[Tag])

    MongoPath.of[Outer](_.middle.inner.tags.each.value) shouldBe "middle.inner.tags.value"
    MongoPath.of[Outer](_.middle.moreTags.each.value) shouldBe "middle.moreTags.value"
    MongoPath.of[Outer](_.evenMoreTags.each.value) shouldBe "evenMoreTags.value"
  }

  // BigDecimal and BigInt tests
  test("MongoPath.of should handle BigDecimal fields") {
    case class Financial(amount: BigDecimal, rate: BigDecimal)
    MongoPath.of[Financial](_.amount) shouldBe "amount"
    MongoPath.of[Financial](_.rate) shouldBe "rate"
  }

  test("MongoPath.of should handle BigInt fields") {
    case class LargeNumbers(value: BigInt, count: BigInt)
    MongoPath.of[LargeNumbers](_.value) shouldBe "value"
    MongoPath.of[LargeNumbers](_.count) shouldBe "count"
  }

  test("MongoPath.of should handle Option[BigDecimal] fields") {
    case class FinancialRecord(amount: Option[BigDecimal])
    MongoPath.of[FinancialRecord](_.amount) shouldBe "amount"
  }

  test("MongoPath.of should handle Seq[BigDecimal] fields") {
    case class PriceHistory(prices: Seq[BigDecimal])
    MongoPath.of[PriceHistory](_.prices) shouldBe "prices"
  }

  // Long and other numeric types
  test("MongoPath.of should handle Long fields") {
    case class Timestamps(created: Long, updated: Long)
    MongoPath.of[Timestamps](_.created) shouldBe "created"
    MongoPath.of[Timestamps](_.updated) shouldBe "updated"
  }

  test("MongoPath.of should handle Float fields") {
    case class Measurements(temperature: Float, humidity: Float)
    MongoPath.of[Measurements](_.temperature) shouldBe "temperature"
    MongoPath.of[Measurements](_.humidity) shouldBe "humidity"
  }

  test("MongoPath.of should handle Byte fields") {
    case class ByteData(flag: Byte, status: Byte)
    MongoPath.of[ByteData](_.flag) shouldBe "flag"
    MongoPath.of[ByteData](_.status) shouldBe "status"
  }

  test("MongoPath.of should handle Short fields") {
    case class ShortData(code: Short, value: Short)
    MongoPath.of[ShortData](_.code) shouldBe "code"
    MongoPath.of[ShortData](_.value) shouldBe "value"
  }

  test("MongoPath.of should handle Char fields") {
    case class CharData(grade: Char, category: Char)
    MongoPath.of[CharData](_.grade) shouldBe "grade"
    MongoPath.of[CharData](_.category) shouldBe "category"
  }

  // Comprehensive type coverage test
  test("MongoPath.of should handle all common Scala types") {
    case class AllTypes(
        stringField: String,
        intField: Int,
        longField: Long,
        doubleField: Double,
        floatField: Float,
        booleanField: Boolean,
        byteField: Byte,
        shortField: Short,
        charField: Char,
        bigDecimalField: BigDecimal,
        bigIntField: BigInt
    )

    MongoPath.of[AllTypes](_.stringField) shouldBe "stringField"
    MongoPath.of[AllTypes](_.intField) shouldBe "intField"
    MongoPath.of[AllTypes](_.longField) shouldBe "longField"
    MongoPath.of[AllTypes](_.doubleField) shouldBe "doubleField"
    MongoPath.of[AllTypes](_.floatField) shouldBe "floatField"
    MongoPath.of[AllTypes](_.booleanField) shouldBe "booleanField"
    MongoPath.of[AllTypes](_.byteField) shouldBe "byteField"
    MongoPath.of[AllTypes](_.shortField) shouldBe "shortField"
    MongoPath.of[AllTypes](_.charField) shouldBe "charField"
    MongoPath.of[AllTypes](_.bigDecimalField) shouldBe "bigDecimalField"
    MongoPath.of[AllTypes](_.bigIntField) shouldBe "bigIntField"
  }

end MongoPathSpec
