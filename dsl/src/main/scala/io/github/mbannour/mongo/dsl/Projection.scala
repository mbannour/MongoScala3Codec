package io.github.mbannour.mongo.dsl

import org.bson.conversions.Bson
import org.bson.{BsonArray, BsonDocument, BsonInt32}

import scala.jdk.CollectionConverters.*

/** Utility for building MongoDB projection specifications.
 *
 * {{{
 * import io.github.mbannour.mongo.dsl.*
 *
 * case class User(name: String, age: Int, passwordHash: String)
 *
 * // Include only name and age, exclude _id
 * val proj = Projection.combine(
 *   field[User](_.name).include,
 *   field[User](_.age).include,
 *   Projection.excludeId
 * )
 * // Result: { "name": 1, "age": 1, "_id": 0 }
 * }}}
 */
object Projection:

  /** Merges multiple projection specifications into a single document. */
  def combine(projections: Bson*): Bson =
    projections.foldLeft(new BsonDocument()) { (acc, proj) =>
      Field.toDoc(proj).entrySet().asScala.foreach { entry =>
        acc.append(entry.getKey, entry.getValue)
      }
      acc
    }

  /** Excludes the `_id` field. */
  val excludeId: Bson = new BsonDocument("_id", new BsonInt32(0))

  /** Includes only the `_id` field. */
  val includeId: Bson = new BsonDocument("_id", new BsonInt32(1))

  /** Returns the first `limit` elements of an array field: `{ field: { $slice: limit } }` */
  def slice(path: String, limit: Int): Bson =
    new BsonDocument(path, new BsonDocument("$slice", new BsonInt32(limit)))

  /** Skips `skip` elements and returns the next `limit` elements: `{ field: { $slice: [skip, limit] } }` */
  def slice(path: String, skip: Int, limit: Int): Bson =
    val arr = new BsonArray(List(new BsonInt32(skip), new BsonInt32(limit)).asJava)
    new BsonDocument(path, new BsonDocument("$slice", arr))
