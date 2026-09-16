package io.github.mbannour.bson.macros

import scala.annotation.StaticAnnotation

/** Marks a case class constructor parameter to be excluded from BSON encoding and decoding.
  *
  * WRITE: the field is never written to the encoded document.
  *
  * READ: the field is never read from the document; the constructor default value is used instead.
  *
  * Example usage:
  * {{{
  * case class User(name: String, @BsonIgnore internalNote: String = "")
  * }}}
  */
final class BsonIgnore extends StaticAnnotation
