package io.github.mbannour.bson.macros

import scala.quoted.*

/** Compile-time utilities for detecting and working with sealed traits and their subtypes.
  */
object SealedTraitHelper:

  /** Checks if a type is a sealed trait or sealed abstract class at compile time.
    *
    * @tparam T
    *   The type to check
    * @return
    *   true if T is a sealed trait or sealed abstract class
    */
  inline def isSealedTrait[T]: Boolean = ${ isSealedTraitImpl[T] }

  private def isSealedTraitImpl[T: Type](using Quotes): Expr[Boolean] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    val isSealed = sym.flags.is(Flags.Sealed)
    val isTraitOrAbstract = sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)

    Expr(isSealed && isTraitOrAbstract)

  /** Gets all direct subtypes of a sealed trait at compile time.
    *
    * @tparam T
    *   The sealed trait type
    * @return
    *   A list of type symbols for all direct subtypes
    */
  def getSealedChildren(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.Symbol] =
    import quotes.reflect.*

    val sym = tpe.typeSymbol

    if !sym.flags.is(Flags.Sealed) then
      report.errorAndAbort(s"Type ${sym.name} is not a sealed trait or sealed abstract class")

    // Get all known children of this sealed type
    sym.children.filter(_.flags.is(Flags.Case)).toList

  /** Creates a mapping from discriminator values to runtime classes for a sealed trait hierarchy.
    *
    * @tparam T
    *   The sealed trait type
    * @param discriminatorStrategy
    *   The strategy for generating discriminator values
    * @return
    *   A map from discriminator string to runtime class
    */
  inline def createDiscriminatorMap[T](discriminatorStrategy: io.github.mbannour.mongo.codecs.DiscriminatorStrategy): Map[String, Class[?]] =
    ${ createDiscriminatorMapImpl[T]('discriminatorStrategy) }

  private def createDiscriminatorMapImpl[T: Type](
      discriminatorStrategy: Expr[io.github.mbannour.mongo.codecs.DiscriminatorStrategy]
  )(using Quotes): Expr[Map[String, Class[?]]] =
    import quotes.reflect.*
    import scala.reflect.ClassTag
    import scala.compiletime.summonInline

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if !sym.flags.is(Flags.Sealed) then
      report.errorAndAbort(s"Type ${sym.name} is not a sealed trait or sealed abstract class")

    // Get all known children
    val children = sym.children.filter(_.flags.is(Flags.Case))

    // Generate mapping entries for each child
    val mappingEntries = children.map { child =>
      val childType = child.typeRef
      childType.asType match
        case '[ct] =>
          val simpleName = child.name
          val fullName = child.fullName

          // Generate discriminator value based on strategy
          '{
            val clazz: Class[?] = summonInline[ClassTag[ct]].runtimeClass.asInstanceOf[Class[?]]
            val discriminator = $discriminatorStrategy match
              case io.github.mbannour.mongo.codecs.DiscriminatorStrategy.SimpleName =>
                ${ Expr(simpleName) }
              case io.github.mbannour.mongo.codecs.DiscriminatorStrategy.FullyQualifiedName =>
                ${ Expr(fullName) }
              case io.github.mbannour.mongo.codecs.DiscriminatorStrategy.Custom(mapping) =>
                mapping.getOrElse(clazz, ${ Expr(simpleName) })
            (discriminator -> clazz)
          }
    }

    '{ Map(${ Varargs(mappingEntries) }*) }

  /** Creates a reverse mapping from runtime classes to discriminator values.
    *
    * @tparam T
    *   The sealed trait type
    * @param discriminatorStrategy
    *   The strategy for generating discriminator values
    * @return
    *   A map from runtime class to discriminator string
    */
  inline def createReverseDiscriminatorMap[T](discriminatorStrategy: io.github.mbannour.mongo.codecs.DiscriminatorStrategy): Map[Class[?], String] =
    ${ createReverseDiscriminatorMapImpl[T]('discriminatorStrategy) }

  private def createReverseDiscriminatorMapImpl[T: Type](
      discriminatorStrategy: Expr[io.github.mbannour.mongo.codecs.DiscriminatorStrategy]
  )(using Quotes): Expr[Map[Class[?], String]] =
    import quotes.reflect.*
    import scala.reflect.ClassTag
    import scala.compiletime.summonInline

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if !sym.flags.is(Flags.Sealed) then
      report.errorAndAbort(s"Type ${sym.name} is not a sealed trait or sealed abstract class")

    // Get all known children
    val children = sym.children.filter(_.flags.is(Flags.Case))

    // Generate mapping entries for each child
    val mappingEntries = children.map { child =>
      val childType = child.typeRef
      childType.asType match
        case '[ct] =>
          val simpleName = child.name
          val fullName = child.fullName

          // Generate discriminator value based on strategy
          '{
            val clazz: Class[?] = summonInline[ClassTag[ct]].runtimeClass.asInstanceOf[Class[?]]
            val discriminator = $discriminatorStrategy match
              case io.github.mbannour.mongo.codecs.DiscriminatorStrategy.SimpleName =>
                ${ Expr(simpleName) }
              case io.github.mbannour.mongo.codecs.DiscriminatorStrategy.FullyQualifiedName =>
                ${ Expr(fullName) }
              case io.github.mbannour.mongo.codecs.DiscriminatorStrategy.Custom(mapping) =>
                mapping.getOrElse(clazz, ${ Expr(simpleName) })
            (clazz -> discriminator)
          }
    }

    '{ Map(${ Varargs(mappingEntries) }*) }

  /** Checks if a field type is a sealed trait that requires discriminator encoding.
    *
    * This is used during codec generation to determine if special sealed trait handling is needed.
    */
  def isFieldSealedTrait(using Quotes)(fieldType: quotes.reflect.TypeRepr): Boolean =
    import quotes.reflect.*

    val sym = fieldType.dealias.typeSymbol
    val isSealed = sym.flags.is(Flags.Sealed)
    val isTraitOrAbstract = sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)

    isSealed && isTraitOrAbstract

  /** Gets the discriminator value for a given concrete type based on the strategy.
    */
  def getDiscriminatorValue(
      clazz: Class[?],
      strategy: io.github.mbannour.mongo.codecs.DiscriminatorStrategy
  ): String =
    strategy match
      case io.github.mbannour.mongo.codecs.DiscriminatorStrategy.SimpleName =>
        clazz.getSimpleName
      case io.github.mbannour.mongo.codecs.DiscriminatorStrategy.FullyQualifiedName =>
        clazz.getName
      case io.github.mbannour.mongo.codecs.DiscriminatorStrategy.Custom(mapping) =>
        mapping.getOrElse(clazz, clazz.getSimpleName)

end SealedTraitHelper
