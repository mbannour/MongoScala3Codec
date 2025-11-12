package io.github.mbannour.bson.macros

import scala.annotation.StaticAnnotation

/** Annotation to configure how a Scala 3 enum should be encoded/decoded to/from BSON.
  *
  * This annotation can be applied to enum parameters in case classes to specify custom serialization behavior.
  *
  * @param nameField
  *   The name of the method/field to use for encoding/decoding. For example, if you have an enum with a custom `code` method, you can
  *   specify `nameField = "code"` to use that method's value for serialization. Default is empty string, which means use the enum's
  *   toString (name).
  *
  * Example usage:
  * {{{
  * enum Status(val code: Int):
  *   case Ok extends Status(200)
  *   case NotFound extends Status(404)
  *
  * case class Response(@BsonEnum(nameField = "code") status: Status)
  * }}}
  */
case class BsonEnum(nameField: String = "") extends StaticAnnotation
