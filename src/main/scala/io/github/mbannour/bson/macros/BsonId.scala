package io.github.mbannour.bson.macros

import scala.annotation.StaticAnnotation

/** Maps a case class constructor parameter to MongoDB's `_id` field.
  *
  * The annotated field is encoded and decoded under the BSON name `_id` regardless of its Scala name. The annotation only controls the
  * field name; any BSON-representable type MongoDB accepts for `_id` may be used.
  *
  * Example usage:
  * {{{
  * case class User(@BsonId id: ObjectId, name: String)
  * }}}
  */
final class BsonId extends StaticAnnotation
