package io.github.mbannour.mongo.dsl

import org.bson.conversions.Bson
import org.bson.BsonDocument

import scala.jdk.CollectionConverters.*

/** Utility for merging multiple MongoDB update operations into a single document.
 *
 * MongoDB update documents group operations by operator (`$set`, `$inc`, etc.).
 * [[combine]] merges them correctly, coalescing operations that share the same operator.
 *
 * {{{
 * import io.github.mbannour.mongo.dsl.*
 *
 * case class User(name: String, age: Int, active: Boolean)
 *
 * val upd = Update.combine(
 *   field[User](_.name)   := "Jane",     // $set: { name: "Jane" }
 *   field[User](_.age).inc(1),            // $inc: { age: 1 }
 *   field[User](_.active) := true         // merged into $set: { name: "Jane", active: true }
 * )
 * // Result: { "$set": { "name": "Jane", "active": true }, "$inc": { "age": 1 } }
 * }}}
 */
object Update:

  /** Merges multiple update operations, coalescing entries under the same operator. */
  def combine(updates: Bson*): Bson =
    updates.foldLeft(new BsonDocument()) { (acc, upd) =>
      val doc = Field.toDoc(upd)
      doc.entrySet().asScala.foreach { entry =>
        val key   = entry.getKey
        val value = entry.getValue
        if acc.containsKey(key) then
          val existing = acc.get(key).asDocument()
          value.asDocument().entrySet().asScala.foreach { inner =>
            existing.put(inner.getKey, inner.getValue)
          }
        else
          acc.append(key, value)
      }
      acc
    }
