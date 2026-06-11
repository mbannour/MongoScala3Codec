package io.github.mbannour.mongo.dsl

import io.github.mbannour.fields.MongoPath

/** Intermediate builder that captures the document type [[Doc]].
 *
 * Created by `field[Doc]`; call `.apply(selector)` (or just `(selector)`) to produce a [[Field]].
 */
final class FieldSelector[Doc]:
  transparent inline def apply[A](inline selector: Doc => A): Field[Doc, A] =
    Field[Doc, A](MongoPath.of[Doc](selector))

/** Creates a typed compile-time reference to a document field.
 *
 * The field type `A` is inferred from the selector lambda, giving you type-safe filter,
 * update, sort, and projection operators without any runtime reflection.
 *
 * === Basic usage ===
 * {{{
 * import io.github.mbannour.mongo.dsl.*
 * import io.github.mbannour.fields.MongoPath.syntax.{?, each}  // for Option/collection navigation
 *
 * case class Address(city: String, @BsonProperty("zip") zipCode: Int)
 * case class User(name: String, age: Int, active: Boolean, address: Option[Address], scores: List[Int])
 *
 * // Field references — type A is inferred
 * val nameF   = field[User](_.name)             // Field[User, String]
 * val ageF    = field[User](_.age)              // Field[User, Int]
 * val cityF   = field[User](_.address.?.city)   // Field[User, String], path = "address.city"
 *
 * // Filters
 * val byName   = nameF === "John"
 * val byAge    = ageF > 25
 * val combined = Filter.and(byName, byAge)
 *
 * // Update
 * val upd = Update.combine(
 *   nameF := "Jane",
 *   ageF.inc(1)
 * )
 *
 * // Sort (age desc, name asc)
 * val sort = Sort.combine(ageF.desc, nameF.asc)
 *
 * // Projection (name and age only, no _id)
 * val proj = Projection.combine(nameF.include, ageF.include, Projection.excludeId)
 * }}}
 */
inline def field[Doc]: FieldSelector[Doc] = new FieldSelector[Doc]
