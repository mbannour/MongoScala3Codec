package io.github.mbannour.bson.macros

import scala.annotation.StaticAnnotation

/** Overrides the discriminator value written for a concrete subtype of a sealed hierarchy.
  *
  * Without it a subtype is recorded under its simple name; with it, under `value`. The annotation changes only the discriminator value -
  * the field it is written under is still `CodecConfig.discriminatorField`, and the payload is untouched.
  *
  * Annotating a subtype changes what is persisted for it, so documents written before the annotation was added are no longer readable for
  * that subtype.
  *
  * Example usage:
  * {{{
  * sealed trait Animal
  * @BsonDiscriminator("dog") case class Dog(name: String) extends Animal
  * case class Cat(name: String) extends Animal
  *
  * // Dog("Rex") -> {"_type": "dog", "name": "Rex"}
  * // Cat("Milo") -> {"_type": "Cat", "name": "Milo"}
  * }}}
  */
final class BsonDiscriminator(val value: String) extends StaticAnnotation
