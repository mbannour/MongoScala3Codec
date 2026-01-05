package io.github.mbannour.mongo.codecs

import scala.annotation.unused
import scala.quoted.*
import scala.reflect.ClassTag

import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

import io.github.mbannour.bson.macros.CaseClassMapper

/** Provides inline macros for generating `CodecProvider` instances for sealed traits and classes.
  *
  * A sealed trait codec provider creates a discriminator-based codec that can encode/decode all concrete case class subtypes of the sealed
  * trait. This is used by `RegistryBuilder.registerSealed[T]` to enable polymorphic serialization.
  *
  * ==Example Usage==
  * {{{
  *   sealed trait Animal
  *   case class Dog(name: String, breed: String) extends Animal
  *   case class Cat(name: String, lives: Int) extends Animal
  *
  *   val provider = SealedCodecProviderMacro.createProvider[Animal](
  *     using classTag,
  *     CodecConfig(),
  *     registry
  *   )
  *
  *   // Provider will return a codec that encodes Dog as {"_type": "Dog", "name": "Rex", "breed": "Lab"}
  * }}}
  *
  * @see
  *   [[SealedTraitCodecGenerator]] for the underlying codec generation logic
  */
object SealedCodecProviderMacro:

  /** Creates a `CodecProvider` for a sealed trait/class type `T`.
    *
    * This provider will:
    *   - Return a discriminator-based codec for the sealed trait
    *   - Handle encoding/decoding of all concrete case class subtypes
    *   - Use the configured discriminator field (default: "_type")
    *
    * @tparam T
    *   The sealed trait or class type
    * @param classTag
    *   Runtime `ClassTag` for `T` (injected implicitly)
    * @param config
    *   Configuration for codec behavior (discriminator field, None handling, etc.)
    * @param codecRegistry
    *   The base `CodecRegistry` used for nested type lookups
    * @return
    *   A `CodecProvider` that supplies a BSON `Codec[T]` for the sealed hierarchy
    *
    * @example
    *   {{{
    *     val provider = SealedCodecProviderMacro.createProvider[Animal](
    *       using summon[ClassTag[Animal]],
    *       CodecConfig(),
    *       registry
    *     )
    *   }}}
    */
  inline def createProvider[T](using
      classTag: ClassTag[T],
      config: CodecConfig,
      codecRegistry: CodecRegistry
  ): CodecProvider =
    ${ createProviderImpl[T]('classTag, 'config, 'codecRegistry) }

  /** Macro implementation for creating a sealed trait codec provider.
    *
    * Validates at compile time that:
    *   - T is a sealed type (trait, class, or abstract class)
    *   - T has at least one concrete case class subtype
    *
    * @param classTag
    *   The ClassTag for type T
    * @param config
    *   Configuration for codec generation
    * @param codecRegistry
    *   The registry (unused in macro, but needed for runtime provider)
    * @return
    *   An expression representing a CodecProvider
    */
  private def createProviderImpl[T: Type](
      classTag: Expr[ClassTag[T]],
      config: Expr[CodecConfig],
      @unused codecRegistry: Expr[CodecRegistry]
  )(using Quotes): Expr[CodecProvider] =
    import quotes.reflect.*

    val mainType = TypeRepr.of[T]
    val mainTypeSymbol = mainType.typeSymbol
    val typeName = mainTypeSymbol.name

    // Validate that T is sealed
    if !mainTypeSymbol.flags.is(Flags.Sealed) then
      val typeKind =
        if mainTypeSymbol.flags.is(Flags.Trait) then "non-sealed trait"
        else if mainTypeSymbol.flags.is(Flags.Abstract) then "non-sealed abstract class"
        else if mainTypeSymbol.flags.is(Flags.Case) then "case class"
        else "type"

      report.errorAndAbort(
        s"Cannot create sealed codec provider for '$typeName'" +
          s"\n\n'$typeName' is a $typeKind, not a sealed type." +
          "\n\n" +
          "Suggestions:" +
          s"\n  • If '$typeName' is a sealed trait, ensure it's declared as: sealed trait $typeName" +
          s"\n  • If '$typeName' is a case class, use .register[$typeName] instead of .registerSealed[$typeName]" +
          "\n  • For non-sealed traits, you cannot use discriminator-based encoding"
      )
    end if

    // Validate that there are concrete case class subtypes
    // This is done at compile-time by CaseClassMapper, which will provide helpful errors
    val caseClassesMapExpr = '{ CaseClassMapper.caseClassMap[T] }

    // Create the provider
    '{
      new CodecProvider:
        /** Returns a sealed trait codec for the given class if it matches the sealed type.
          *
          * The codec handles:
          *   - Writing discriminator field during encoding
          *   - Reading discriminator field during decoding
          *   - Delegating to appropriate concrete case class codec
          *
          * @param clazz
          *   The runtime class being requested
          * @param registry
          *   The codec registry (used for looking up concrete subclass codecs)
          * @return
          *   A codec for the sealed trait, or null if the class doesn't match
          */
        @SuppressWarnings(Array("unchecked"))
        def get[C](clazz: Class[C], registry: CodecRegistry): Codec[C] =
          if $classTag.runtimeClass == clazz then
            SealedTraitCodecGenerator
              .generateSealedCodec[T]($config, registry)(using $classTag)
              .asInstanceOf[Codec[C]]
          else null
    }
  end createProviderImpl

  /** Creates providers for all concrete case class subtypes of a sealed type.
    *
    * This is used by `RegistryBuilder.registerSealed[T]` to automatically register codecs for all concrete subtypes alongside the sealed
    * trait codec.
    *
    * @tparam T
    *   The sealed trait/class type
    * @param config
    *   Configuration for codec generation
    * @param codecRegistry
    *   The base codec registry
    * @return
    *   A vector of CodecProvider instances, one for each concrete case class subtype
    *
    * @example
    *   {{{
    *     sealed trait Animal
    *     case class Dog(...) extends Animal
    *     case class Cat(...) extends Animal
    *
    *     // Creates providers for both Dog and Cat
    *     val subProviders = SealedCodecProviderMacro.createSubclassProviders[Animal](config, registry)
    *   }}}
    */
  inline def createSubclassProviders[T](
      config: CodecConfig,
      codecRegistry: CodecRegistry
  ): Vector[CodecProvider] =
    ${ createSubclassProvidersImpl[T]('config, 'codecRegistry) }

  /** Macro implementation for creating subclass providers.
    *
    * At compile time:
    *   1. Discovers all concrete case class subtypes of T 2. Generates a CodecProviderMacro.createCodecProvider for each subtype 3. Returns
    *      a vector of all providers
    *
    * @param config
    *   Configuration for codec generation
    * @param codecRegistry
    *   The codec registry expression
    * @return
    *   An expression representing a Vector[CodecProvider]
    */
  private def createSubclassProvidersImpl[T: Type](
      config: Expr[CodecConfig],
      codecRegistry: Expr[CodecRegistry]
  )(using Quotes): Expr[Vector[CodecProvider]] =
    import quotes.reflect.*

    val mainType = TypeRepr.of[T]
    val mainSymbol = mainType.typeSymbol

    def isCaseClass(symbol: Symbol): Boolean =
      symbol.isClassDef && symbol.flags.is(Flags.Case)

    def subclasses(symbol: Symbol): Set[Symbol] =
      val directSubclasses = symbol.children.toSet
      directSubclasses ++ directSubclasses.flatMap(subclasses)

    val caseClassSymbols: List[Symbol] =
      if mainSymbol.flags.is(Flags.Sealed) then subclasses(mainSymbol).filter(isCaseClass).toList
      else List.empty

    val providerExprs: List[Expr[CodecProvider]] = caseClassSymbols.map { subSymbol =>
      subSymbol.typeRef.asType match
        case '[subType] =>
          // Create CodecProvider for this concrete subclass
          Expr.summon[ClassTag[subType]] match
            case Some(classTagExpr) =>
              '{
                CodecProviderMacro.createCodecProvider[subType](using
                  $classTagExpr,
                  $config,
                  $codecRegistry
                )
              }
            case None =>
              report.errorAndAbort(s"Cannot summon ClassTag for ${subSymbol.fullName}")
    }

    Expr.ofList(providerExprs) match
      case '{ $list: List[CodecProvider] } => '{ $list.toVector }
  end createSubclassProvidersImpl

end SealedCodecProviderMacro
