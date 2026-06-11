package io.github.mbannour.mongo.dsl.testkit

import io.github.mbannour.mongo.dsl.*
import org.bson.{BsonDocument, BsonInt32, BsonString}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DslTestKitSpec extends AnyFunSuite with Matchers:

  case class User(name: String, age: Int, active: Boolean, scores: List[Int])

  // ── assertFilter ──────────────────────────────────────────────────────────

  test("assertFilter passes for a correct equality filter") {
    val f = field[User](_.name) === "Alice"
    DslTestKit.assertFilter(f, "name", "$eq", new BsonString("Alice"))
  }

  test("assertFilter passes for a numeric comparison filter") {
    val f = field[User](_.age) > 18
    DslTestKit.assertFilter(f, "age", "$gt", new BsonInt32(18))
  }

  test("assertFilter fails with a clear message when key is absent") {
    val f  = field[User](_.name) === "Alice"
    val ex = intercept[IllegalArgumentException](DslTestKit.assertFilter(f, "wrong_key", "$eq", new BsonString("Alice")))
    assert(ex.getMessage.contains("wrong_key"))
  }

  test("assertFilter fails with a clear message when operator is absent") {
    val f  = field[User](_.age) > 18
    val ex = intercept[IllegalArgumentException](DslTestKit.assertFilter(f, "age", "$eq", new BsonInt32(18)))
    assert(ex.getMessage.contains("$eq") || ex.getMessage.contains("age"))
  }

  test("assertFilter fails when value does not match") {
    val f  = field[User](_.name) === "Alice"
    val ex = intercept[IllegalArgumentException](DslTestKit.assertFilter(f, "name", "$eq", new BsonString("Bob")))
    assert(ex.getMessage.contains("Bob"))
  }

  // ── assertUpdate ─────────────────────────────────────────────────────────

  test("assertUpdate passes for a $set update") {
    val u = field[User](_.name) := "Bob"
    DslTestKit.assertUpdate(u, "$set", "name", new BsonString("Bob"))
  }

  test("assertUpdate passes for a $inc update") {
    val u = field[User](_.age).inc(5)
    DslTestKit.assertUpdate(u, "$inc", "age", new BsonInt32(5))
  }

  test("assertUpdate fails when operator is absent") {
    val u  = field[User](_.name) := "Bob"
    val ex = intercept[IllegalArgumentException](DslTestKit.assertUpdate(u, "$inc", "name", new BsonString("Bob")))
    assert(ex.getMessage.contains("$inc"))
  }

  test("assertUpdate fails when field key is absent under operator") {
    val u  = field[User](_.name) := "Bob"
    val ex = intercept[IllegalArgumentException](DslTestKit.assertUpdate(u, "$set", "age", new BsonString("Bob")))
    assert(ex.getMessage.contains("age"))
  }

  // ── assertSort ────────────────────────────────────────────────────────────

  test("assertSort passes for ascending") {
    DslTestKit.assertSort(field[User](_.age).asc, "age", ascending = true)
  }

  test("assertSort passes for descending") {
    DslTestKit.assertSort(field[User](_.name).desc, "name", ascending = false)
  }

  test("assertSort fails when direction is wrong") {
    val ex = intercept[IllegalArgumentException](DslTestKit.assertSort(field[User](_.age).desc, "age", ascending = true))
    assert(ex.getMessage.contains("DESC"))
  }

  // ── assertProjection ──────────────────────────────────────────────────────

  test("assertProjection passes for include") {
    DslTestKit.assertProjection(field[User](_.name).include, "name", include = true)
  }

  test("assertProjection passes for exclude") {
    DslTestKit.assertProjection(field[User](_.name).exclude, "name", include = false)
  }

  test("assertProjection fails when direction is wrong") {
    val ex = intercept[IllegalArgumentException](DslTestKit.assertProjection(field[User](_.name).exclude, "name", include = true))
    assert(ex.getMessage.contains("include"))
  }

  // ── assertHasKey / assertMissingKey ──────────────────────────────────────

  test("assertHasKey passes when key exists") {
    DslTestKit.assertHasKey(field[User](_.name) === "Alice", "name")
  }

  test("assertHasKey fails when key is absent") {
    val ex = intercept[IllegalArgumentException](DslTestKit.assertHasKey(field[User](_.name) === "Alice", "age"))
    assert(ex.getMessage.contains("age"))
  }

  test("assertMissingKey passes when key is absent") {
    DslTestKit.assertMissingKey(field[User](_.name) === "Alice", "age")
  }

  test("assertMissingKey fails when key exists") {
    val ex = intercept[IllegalArgumentException](DslTestKit.assertMissingKey(field[User](_.name) === "Alice", "name"))
    assert(ex.getMessage.contains("name"))
  }

  // ── toJson / toMap / render ───────────────────────────────────────────────

  test("toJson returns valid JSON containing the key and operator") {
    val json = DslTestKit.toJson(field[User](_.name) === "Alice")
    assert(json.contains("name") && json.contains("$eq") && json.contains("Alice"))
  }

  test("render returns the same result as toJson") {
    val bson = field[User](_.age) > 18
    DslTestKit.render(bson) shouldBe DslTestKit.toJson(bson)
  }

  test("toMap extracts top-level entries") {
    val m = DslTestKit.toMap(field[User](_.active) === true)
    m.size shouldBe 1
    m.keys should contain("active")
  }

  // ── assertEqualsJson ──────────────────────────────────────────────────────

  test("assertEqualsJson passes for a matching equality filter") {
    DslTestKit.assertEqualsJson(
      field[User](_.name) === "Alice",
      """{"name": {"$eq": "Alice"}}"""
    )
  }

  test("assertEqualsJson passes for a numeric comparison filter") {
    DslTestKit.assertEqualsJson(
      field[User](_.age) > 18,
      """{"age": {"$gt": 18}}"""
    )
  }

  test("assertEqualsJson passes for a boolean equality filter") {
    DslTestKit.assertEqualsJson(
      field[User](_.active) === true,
      """{"active": {"$eq": true}}"""
    )
  }

  test("assertEqualsJson passes for Filter.and") {
    DslTestKit.assertEqualsJson(
      Filter.and(field[User](_.active) === true, field[User](_.age) > 18),
      """{"$and": [{"active": {"$eq": true}}, {"age": {"$gt": 18}}]}"""
    )
  }

  test("assertEqualsJson passes for a $set update") {
    DslTestKit.assertEqualsJson(
      field[User](_.name) := "Bob",
      """{"$set": {"name": "Bob"}}"""
    )
  }

  test("assertEqualsJson passes for Update.combine") {
    DslTestKit.assertEqualsJson(
      Update.combine(
        field[User](_.name) := "Bob",
        field[User](_.age).inc(1)
      ),
      """{"$set": {"name": "Bob"}, "$inc": {"age": 1}}"""
    )
  }

  test("assertEqualsJson fails with a clear diff when JSON does not match") {
    val ex = intercept[IllegalArgumentException]:
      DslTestKit.assertEqualsJson(
        field[User](_.name) === "Alice",
        """{"name": {"$eq": "Bob"}}"""
      )
    assert(ex.getMessage.contains("expected"))
    assert(ex.getMessage.contains("actual"))
  }

  // ── assertEquivalentTo ────────────────────────────────────────────────────

  test("assertEquivalentTo passes when two DSL expressions produce the same document") {
    // Both produce {"name": {"$eq": "Alice"}}
    val a = field[User](_.name) === "Alice"
    val b = new org.bson.BsonDocument("name",
              new org.bson.BsonDocument("$eq", new BsonString("Alice")))
    DslTestKit.assertEquivalentTo(a, b)
  }

  test("assertEquivalentTo passes when comparing DSL filter with a hand-written BsonDocument") {
    import org.bson.{BsonDocument, BsonInt32}
    val dslSort = field[User](_.age).desc
    val manualSort = new BsonDocument("age", new BsonInt32(-1))
    DslTestKit.assertEquivalentTo(dslSort, manualSort)
  }

  test("assertEquivalentTo fails with a diff when documents differ") {
    val a = field[User](_.name) === "Alice"
    val b = field[User](_.name) === "Bob"
    val ex = intercept[IllegalArgumentException](DslTestKit.assertEquivalentTo(a, b))
    assert(ex.getMessage.contains("reference"))
    assert(ex.getMessage.contains("actual"))
  }

  // ── error on non-BsonDocument input to innerDoc ───────────────────────────

  test("innerDoc throws IllegalArgumentException for non-BsonDocument Bson") {
    // Implements Bson but is not a BsonDocument
    val nonDoc: org.bson.conversions.Bson = new org.bson.conversions.Bson:
      def toBsonDocument[T](
        documentClass: Class[T],
        codecRegistry: org.bson.codecs.configuration.CodecRegistry
      ): org.bson.BsonDocument = new org.bson.BsonDocument()
    val ex = intercept[IllegalArgumentException](DslTestKit.innerDoc(nonDoc, "any"))
    assert(ex.getMessage.contains("BsonDocument"))
  }

end DslTestKitSpec
