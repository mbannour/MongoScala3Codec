package io.github.mbannour.mongo.dsl

import org.bson.conversions.Bson
import org.bson.{BsonDocument, BsonInt32, BsonString}

import scala.jdk.CollectionConverters.*

/** Utility for building and combining MongoDB sort specifications.
 *
 * {{{
 * import io.github.mbannour.mongo.dsl.*
 *
 * case class User(name: String, age: Int)
 *
 * // Sort by age descending, then name ascending
 * val sort = Sort.combine(
 *   field[User](_.age).desc,
 *   field[User](_.name).asc
 * )
 * // Result: { "age": -1, "name": 1 }
 * }}}
 */
object Sort:

  /** Merges multiple sort specifications into a single document. Field order is preserved. */
  def combine(sorts: Bson*): Bson =
    sorts.foldLeft(new BsonDocument()) { (acc, sort) =>
      Field.toDoc(sort).entrySet().asScala.foreach { entry =>
        acc.append(entry.getKey, entry.getValue)
      }
      acc
    }

  /** Ascending sort on a raw path string (useful when you have the path but not a typed field). */
  def ascending(path: String): Bson  = new BsonDocument(path, new BsonInt32(1))

  /** Descending sort on a raw path string. */
  def descending(path: String): Bson = new BsonDocument(path, new BsonInt32(-1))

  /** Sort by text search score (for use with `$text` queries). */
  val byTextScore: Bson = new BsonDocument("score", new BsonDocument("$meta", new BsonString("textScore")))

  /** Sort in natural insertion order (forward). */
  val natural: Bson = new BsonDocument("$natural", new BsonInt32(1))

  /** Sort in reverse natural insertion order. */
  val naturalDesc: Bson = new BsonDocument("$natural", new BsonInt32(-1))
