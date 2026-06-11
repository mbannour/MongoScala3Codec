package io.github.mbannour.mongo.dsl

import org.bson.conversions.Bson
import org.bson.{BsonArray, BsonBoolean, BsonDocument, BsonString}

import scala.jdk.CollectionConverters.*

/** Logical filter combinators for building compound MongoDB query filters.
 *
 * {{{
 * import io.github.mbannour.mongo.dsl.*
 *
 * case class User(name: String, age: Int, active: Boolean)
 *
 * val f = Filter.and(
 *   field[User](_.name) === "John",
 *   Filter.or(
 *     field[User](_.age) > 18,
 *     field[User](_.active) === true
 *   )
 * )
 * }}}
 */
object Filter:

  /** A filter that matches all documents. */
  val empty: Bson = new BsonDocument()

  /** Combines filters with logical AND. Returns the single filter unchanged when given one argument. */
  def and(filters: Bson*): Bson =
    filters.toList match
      case Nil         => new BsonDocument()
      case head :: Nil => head
      case docs        => new BsonDocument("$and", new BsonArray(docs.map(Field.toDoc).asJava))

  /** Combines filters with logical OR. Returns the single filter unchanged when given one argument. */
  def or(filters: Bson*): Bson =
    filters.toList match
      case Nil         => new BsonDocument()
      case head :: Nil => head
      case docs        => new BsonDocument("$or", new BsonArray(docs.map(Field.toDoc).asJava))

  /** Combines filters with logical NOR (none of the conditions must match). */
  def nor(filters: Bson*): Bson =
    new BsonDocument("$nor", new BsonArray(filters.map(Field.toDoc).toList.asJava))

  /** Negates a filter — equivalent to `$nor: [filter]`. */
  def not(filter: Bson): Bson =
    new BsonDocument("$nor", new BsonArray(List(Field.toDoc(filter)).asJava))

  /** Full-text search filter. Requires a text index on the queried collection.
   *
   * @param search             the search string
   * @param language           optional language override (e.g. `"english"`, `"fr"`)
   * @param caseSensitive      if `true`, the search is case-sensitive
   * @param diacriticSensitive if `true`, distinguish characters with/without diacritics
   */
  def text(
    search: String,
    language: Option[String] = None,
    caseSensitive: Option[Boolean] = None,
    diacriticSensitive: Option[Boolean] = None
  ): Bson =
    val inner = new BsonDocument("$search", new BsonString(search))
    language.foreach(l => inner.append("$language", new BsonString(l)))
    caseSensitive.foreach(c => inner.append("$caseSensitive", new BsonBoolean(c)))
    diacriticSensitive.foreach(d => inner.append("$diacriticSensitive", new BsonBoolean(d)))
    new BsonDocument("$text", inner)

  /** Match documents satisfying a JavaScript expression.
   *
   * `$where` is deprecated since MongoDB 5.1 and may be removed in a future release.
   * Prefer typed DSL predicates via [[and]] / [[or]] instead.
   */
  @deprecated("`$where` is deprecated since MongoDB 5.1. Use typed DSL predicates instead.", "0.0.12")
  def where(jsExpression: String): Bson =
    new BsonDocument("$where", new BsonString(jsExpression))
