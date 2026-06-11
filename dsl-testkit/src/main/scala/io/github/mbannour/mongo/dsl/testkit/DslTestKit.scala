package io.github.mbannour.mongo.dsl.testkit

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{BsonValueCodecProvider, DocumentCodecProvider, ValueCodecProvider}
import org.bson.conversions.Bson
import org.bson.{BsonDocument, BsonValue}

/** Testing utilities for code that uses the [[io.github.mbannour.mongo.dsl]] DSL.
 *
 * Provides helpers for asserting the structure of BSON documents produced by DSL operators
 * without requiring a live MongoDB instance.
 *
 * The two most common usage patterns are:
 *
 * === 1. Structural assertions ===
 * {{{
 * import io.github.mbannour.mongo.dsl.*
 * import io.github.mbannour.mongo.dsl.testkit.DslTestKit
 * import org.bson.{BsonInt32, BsonString}
 *
 * case class User(name: String, age: Int)
 *
 * DslTestKit.assertFilter(field[User](_.name) === "Alice", "name", "$eq", new BsonString("Alice"))
 * DslTestKit.assertUpdate(field[User](_.age).inc(1), "$inc", "age", new BsonInt32(1))
 * }}}
 *
 * === 2. JSON / equivalence comparison ===
 * {{{
 * // Compare against an expected MongoDB query JSON string
 * DslTestKit.assertEqualsJson(
 *   field[User](_.name) === "Alice",
 *   """{"name": {"$eq": "Alice"}}"""
 * )
 *
 * // Compare against the MongoDB driver's own Filters API
 * import com.mongodb.client.model.Filters
 * DslTestKit.assertEquivalentTo(
 *   field[User](_.name) === "Alice",
 *   Filters.eq("name", "Alice")
 * )
 * }}}
 */
object DslTestKit:

  /** Extract the inner `BsonDocument` stored under `key` in the top-level document.
   *
   * Throws [[IllegalArgumentException]] with a descriptive message on mismatch.
   */
  def innerDoc(bson: Bson, key: String): BsonDocument =
    val doc = toDoc(bson)
    require(
      doc.containsKey(key),
      s"Expected top-level key '$key' in document but got: ${doc.toJson()}"
    )
    val value = doc.get(key)
    require(
      value.isDocument,
      s"Expected '$key' to be a BsonDocument but got ${value.getBsonType}: ${doc.toJson()}"
    )
    value.asDocument()

  /** Assert that a filter has the structure `{ key: { operator: expectedValue } }`.
   *
   * @param bson          the filter produced by a DSL operator
   * @param key           the field path (e.g. `"name"`, `"address.city"`)
   * @param operator      the MongoDB operator (e.g. `"$eq"`, `"$gt"`)
   * @param expectedValue the expected encoded [[BsonValue]]
   */
  def assertFilter(bson: Bson, key: String, operator: String, expectedValue: BsonValue): Unit =
    val inner = innerDoc(bson, key)
    require(
      inner.containsKey(operator),
      s"Expected operator '$operator' under key '$key' but got: ${inner.toJson()}"
    )
    val actual = inner.get(operator)
    require(
      actual == expectedValue,
      s"Filter mismatch for '$key.$operator': expected $expectedValue but got $actual"
    )

  /** Assert that an update has the structure `{ operator: { key: expectedValue } }`.
   *
   * @param bson          the update produced by a DSL operator (`:=`, `inc`, `push`, etc.)
   * @param operator      the MongoDB update operator (e.g. `"$set"`, `"$inc"`, `"$push"`)
   * @param key           the target field path
   * @param expectedValue the expected encoded [[BsonValue]]
   */
  def assertUpdate(bson: Bson, operator: String, key: String, expectedValue: BsonValue): Unit =
    val operatorDoc = innerDoc(bson, operator)
    require(
      operatorDoc.containsKey(key),
      s"Expected field key '$key' under update operator '$operator' but got: ${operatorDoc.toJson()}"
    )
    val actual = operatorDoc.get(key)
    require(
      actual == expectedValue,
      s"Update mismatch for '$operator.$key': expected $expectedValue but got $actual"
    )

  /** Assert that a sort document has `{ key: 1 }` (ascending) or `{ key: -1 }` (descending).
   *
   * @param bson      a sort specification produced by `.asc`, `.desc`, or [[Sort.combine]]
   * @param key       the field path to check
   * @param ascending `true` for ascending order (1), `false` for descending (-1)
   */
  def assertSort(bson: Bson, key: String, ascending: Boolean): Unit =
    val doc = toDoc(bson)
    require(
      doc.containsKey(key),
      s"Expected sort key '$key' in document but got: ${doc.toJson()}"
    )
    val actual      = doc.get(key)
    val expectedDir = if ascending then 1 else -1
    val actualLabel = if actual.isInt32 then (if actual.asInt32().getValue == 1 then "ASC (1)" else "DESC (-1)") else actual.toString
    require(
      actual.isInt32 && actual.asInt32().getValue == expectedDir,
      s"Sort direction mismatch for '$key': expected ${if ascending then "ASC (1)" else "DESC (-1)"} but got $actualLabel"
    )

  /** Assert that a projection document has `{ key: 1 }` (include) or `{ key: 0 }` (exclude).
   *
   * @param bson    a projection produced by `.include`, `.exclude`, or [[Projection.combine]]
   * @param key     the field path to check
   * @param include `true` to assert inclusion (1), `false` to assert exclusion (0)
   */
  def assertProjection(bson: Bson, key: String, include: Boolean): Unit =
    val doc = toDoc(bson)
    require(
      doc.containsKey(key),
      s"Expected projection key '$key' in document but got: ${doc.toJson()}"
    )
    val actual      = doc.get(key)
    val expectedBit = if include then 1 else 0
    require(
      actual.isInt32 && actual.asInt32().getValue == expectedBit,
      s"Projection mismatch for '$key': expected ${if include then "include (1)" else "exclude (0)"} but got $actual"
    )

  /** Assert that a [[Bson]] document contains a given top-level key.
   *
   * Useful as a quick sanity-check when the exact value is not important.
   */
  def assertHasKey(bson: Bson, key: String): Unit =
    val doc = toDoc(bson)
    require(
      doc.containsKey(key),
      s"Expected key '$key' to be present in document but got: ${doc.toJson()}"
    )

  /** Assert that a [[Bson]] document does NOT contain a given top-level key. */
  def assertMissingKey(bson: Bson, key: String): Unit =
    val doc = toDoc(bson)
    require(
      !doc.containsKey(key),
      s"Expected key '$key' to be absent from document but it was present: ${doc.toJson()}"
    )

  /** Assert that the DSL [[Bson]] expression produces a document equal to the parsed `expectedJson`.
   *
   * The JSON string is parsed with [[BsonDocument.parse]], which accepts both relaxed and
   * strict extended JSON (e.g. `{"age": {"$gt": 18}}` or `{"age": {"$gt": {"$numberInt": "18"}}}`).
   * Comparison is done after normalising both sides through the same JSON renderer.
   *
   * {{{
   * DslTestKit.assertEqualsJson(
   *   field[User](_.name) === "Alice",
   *   """{"name": {"$eq": "Alice"}}"""
   * )
   * DslTestKit.assertEqualsJson(
   *   Filter.and(field[User](_.age) > 18, field[User](_.active) === true),
   *   """{"$and": [{"age": {"$gt": 18}}, {"active": {"$eq": true}}]}"""
   * )
   * }}}
   *
   * @param bson         the [[Bson]] produced by a DSL expression
   * @param expectedJson a MongoDB query JSON string describing the expected document structure
   */
  def assertEqualsJson(bson: Bson, expectedJson: String): Unit =
    val actualJson   = render(bson)
    val expectedNorm = BsonDocument.parse(expectedJson).toJson()
    require(
      actualJson == expectedNorm,
      s"Filter JSON mismatch:\n  expected : $expectedNorm\n  actual   : $actualJson"
    )

  /** Assert that two [[Bson]] values produce structurally identical documents.
   *
   * Both values are resolved to [[BsonDocument]] via a built-in BSON codec registry that
   * handles `BsonValue` types, Java primitives, and `String` — the types used internally
   * by the MongoDB driver's `Filters.*` and `Updates.*` helpers.
   *
   * This lets you cross-check a DSL expression against the MongoDB driver's own API:
   *
   * {{{
   * import com.mongodb.client.model.Filters
   *
   * // Verify that the DSL produces the same BSON as the official Filters builder
   * DslTestKit.assertEquivalentTo(
   *   field[User](_.name) === "Alice",
   *   Filters.eq("name", "Alice")
   * )
   * DslTestKit.assertEquivalentTo(
   *   field[User](_.age) > 25,
   *   Filters.gt("age", 25)
   * )
   * }}}
   *
   * Note: `Filters.equal` in MongoDB driver 4.x+ renders as `{ field: { $eq: value } }`,
   * which matches the DSL's explicit operator form. Older drivers using the shorthand
   * `{ field: value }` would not match.
   *
   * @param bson      the [[Bson]] produced by a DSL expression
   * @param reference any [[Bson]] to compare against, e.g. `Filters.equal(...)`, `Updates.set(...)`,
   *                  another DSL expression, or a hand-written [[BsonDocument]]
   */
  def assertEquivalentTo(bson: Bson, reference: Bson): Unit =
    val actualJson    = render(bson)
    val referenceJson = render(reference)
    require(
      actualJson == referenceJson,
      s"Bson equivalence mismatch:\n  reference : $referenceJson\n  actual    : $actualJson"
    )

  /** Render a [[Bson]] value to a normalised JSON string.
   *
   * Works with any [[Bson]] implementation, including those produced by
   * `com.mongodb.client.model.Filters`, `Updates`, etc.
   * Uses a built-in codec registry that handles `BsonValue` types, `String`,
   * and Java numeric types.
   */
  def render(bson: Bson): String =
    resolveToBsonDoc(bson).toJson()

  /** Render the [[Bson]] value as a JSON string — useful for test failure messages. */
  def toJson(bson: Bson): String = render(bson)

  /** Extract a flat `Map[String, BsonValue]` of the top-level entries for pattern-matching. */
  def toMap(bson: Bson): Map[String, BsonValue] =
    import scala.jdk.CollectionConverters.*
    toDoc(bson).entrySet().asScala.map(e => e.getKey -> e.getValue).toMap

  // ── Internal helpers ──────────────────────────────────────────────────────

  /** Resolve any [[Bson]] to a [[BsonDocument]] using a minimal built-in registry. */
  private def resolveToBsonDoc(bson: Bson): BsonDocument = bson match
    case doc: BsonDocument => doc
    case other             => other.toBsonDocument(classOf[BsonDocument], defaultRegistry)

  /** Resolve [[Bson]] to a [[BsonDocument]], throwing if the value is not already one.
   *
   * Use [[resolveToBsonDoc]] when the input may come from outside the DSL.
   */
  private def toDoc(bson: Bson): BsonDocument = bson match
    case doc: BsonDocument => doc
    case other =>
      throw new IllegalArgumentException(
        s"DslTestKit expects a BsonDocument produced by the DSL but received ${other.getClass.getName}"
      )

  /** A minimal [[CodecRegistry]] sufficient to resolve MongoDB driver [[Bson]] implementations
   *  (e.g. `Filters.equal`, `Updates.set`) that encode Java/Scala primitives internally.
   */
  private val defaultRegistry: CodecRegistry =
    CodecRegistries.fromProviders(
      new BsonValueCodecProvider(),
      new ValueCodecProvider(),
      new DocumentCodecProvider()
    )
