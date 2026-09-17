package io.github.mbannour.mongo.codecs

import scala.annotation.unused
import scala.quoted.*
import scala.reflect.ClassTag

import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

import io.github.mbannour.bson.macros.PathDependentTypes
import io.github.mbannour.mongo.codecs.CaseClassCodecGenerator.generateCodec

/** `CodecProviderMacro` is a utility object that provides inline macros for generating MongoDB `CodecProvider` instances for Scala case
  * classes.
  *
  * A `CodecProvider` wraps a generated `Codec[T]` so it can be plugged into the MongoDB driver's `CodecRegistry`, allowing seamless
  * serialization and deserialization of your domain models.
  *
  * ==Quick Start==
  * In your case class companion: {{ import io.github.mbannour.mongo.codecs.{CodecProviderMacro, CodecConfig, NoneHandling} import
  * org.bson.codecs.configuration.CodecRegistries import org.mongodb.scala.MongoClient
  *
  * case class Person(name: String, age: Int, nickname: Option[String])
  *
  * object Person: private val config = CodecConfig(noneHandling = NoneHandling.Encode) private val provider =
  * CodecProviderMacro.createCodecProvider[Person]
  *
  * given registry: CodecRegistry = CodecRegistries.fromRegistries( MongoClient.DEFAULT_CODEC_REGISTRY,
  * CodecRegistries.fromProviders(provider) ) end Person }}
  *
  * @see
  *   [[CaseClassCodecGenerator.generateCodec]] for the underlying codec generator.
  */
object CodecProviderMacro:

  /** Creates a `CodecProvider` for type `T` using the specified configuration.
    *
    * @tparam T
    *   The case class type for which to generate the provider.
    * @param classTag
    *   Runtime `ClassTag` for `T` (injected implicitly).
    * @param config
    *   Configuration for codec generation behavior.
    * @param codecRegistry
    *   The base `CodecRegistry` used for nested type lookups.
    * @return
    *   A `CodecProvider` that will supply a BSON `Codec[T]`.
    *
    * @example
    *   {{ val provider = CodecProviderMacro.createCodecProvider[Person](using classTag, CodecConfig(), registry) }}
    */
  inline def createCodecProvider[T](using
      classTag: ClassTag[T],
      config: CodecConfig,
      codecRegistry: CodecRegistry
  ): CodecProvider =
    ${ createCodecProviderImpl[T]('classTag, 'config, 'codecRegistry) }

  /** Creates a `CodecProvider` for type `T` that **ignores** `None` values during serialization.
    *
    * @deprecated
    *   Use `createCodecProvider[T](using classTag, CodecConfig(noneHandling = NoneHandling.Ignore), registry)` instead.
    * @tparam T
    *   The case class type for which to generate the provider.
    * @param classTag
    *   Runtime `ClassTag` for `T` (injected implicitly).
    * @param codecRegistry
    *   The base `CodecRegistry` used for nested type lookups.
    * @return
    *   A `CodecProvider` that will supply a BSON `Codec[T]` omitting fields with `None` values.
    */
  inline def createCodecProviderIgnoreNone[T](using
      classTag: ClassTag[T],
      codecRegistry: CodecRegistry
  ): CodecProvider =
    createCodecProvider[T](using classTag, CodecConfig(noneHandling = NoneHandling.Ignore), codecRegistry)

  /** Creates a `CodecProvider` for type `T` that **encodes** `None` values as BSON `null`.
    *
    * @deprecated
    *   Use `createCodecProvider[T](using classTag, CodecConfig(noneHandling = NoneHandling.Encode), registry)` instead.
    * @tparam T
    *   The case class type for which to generate the provider.
    * @param classTag
    *   Runtime `ClassTag` for `T` (injected implicitly).
    * @param codecRegistry
    *   The base `CodecRegistry` used for nested type lookups.
    * @return
    *   A `CodecProvider` that will supply a BSON `Codec[T]` including `None` fields.
    */
  inline def createCodecProviderEncodeNone[T](using
      classTag: ClassTag[T],
      codecRegistry: CodecRegistry
  ): CodecProvider =
    createCodecProvider[T](using classTag, CodecConfig(noneHandling = NoneHandling.Encode), codecRegistry)

  /** Macro to create a CodecProvider for a case class of type T. Ensures that only case classes are supported, and generates a codec using
    * the provided parameters.
    *
    * This macro validates at compile-time that:
    *   - T is a concrete case class (not a trait or abstract class)
    *   - T is not a generic type parameter without proper bounds
    *
    * @param classTag
    *   The ClassTag for type T
    * @param config
    *   Configuration for codec generation
    * @param codecRegistry
    *   The registry to use for nested codecs
    */
  private def createCodecProviderImpl[T: Type](
      classTag: Expr[ClassTag[T]],
      config: Expr[CodecConfig],
      @unused codecRegistry: Expr[CodecRegistry]
  )(using Quotes) =
    import quotes.reflect.*

    PathDependentTypes.rejectIfPathDependent[T]

    val mainType = TypeRepr.of[T]
    val mainTypeSymbol = mainType.typeSymbol

    val typeName = mainTypeSymbol.name

    // `register` derives a codec from a case class's primary constructor, so anything else is refused here.
    // What the user should do instead depends entirely on what they passed: a sealed trait has its subtypes
    // registered, an enum has its own provider, and only a plain class is really being asked to become a
    // case class. One check, so the kind that is reported is the kind that was found.
    if !mainTypeSymbol.flags.is(Flags.Case) then
      val remediation =
        if mainTypeSymbol.flags.is(Flags.Enum) then
          "\n\nA Scala 3 enum is encoded by its own provider rather than by deriving one:" +
            s"\n  CodecRegistries.fromProviders(EnumValueCodecProvider.forStringEnum[$typeName])" +
            s"\n  CodecRegistries.fromProviders(EnumValueCodecProvider.forOrdinalEnum[$typeName])"
        else if mainTypeSymbol.flags.is(Flags.Sealed) then
          s"\n\nSuggestion: a sealed hierarchy is registered as a whole, which records each subtype under a discriminator:" +
            s"\n  RegistryBuilder.from(...).registerSealed[$typeName].build"
        else if mainTypeSymbol.flags.is(Flags.Trait) || mainTypeSymbol.flags.is(Flags.Abstract) then
          "\n\nSuggestions:" +
            s"\n  • Seal '$typeName' and register the hierarchy: sealed trait $typeName, then .registerSealed[$typeName]" +
            "\n  • Or register each concrete case class that implements it, one by one"
        else
          "\n\nSuggestions:" +
            s"\n  • Declare it as a case class: case class $typeName(...)" +
            s"\n  • Or supply a codec for it yourself: CodecRegistries.fromCodecs(new MyCodec)"

      val kind =
        if mainTypeSymbol.flags.is(Flags.Enum) then "an enum"
        else if mainTypeSymbol.flags.is(Flags.Sealed) && mainTypeSymbol.flags.is(Flags.Trait) then "a sealed trait"
        else if mainTypeSymbol.flags.is(Flags.Sealed) then "a sealed class"
        else if mainTypeSymbol.flags.is(Flags.Trait) then "a trait"
        else if mainTypeSymbol.flags.is(Flags.Abstract) then "an abstract class"
        else if mainTypeSymbol.isClassDef then "a class"
        else "a type"

      report.errorAndAbort(
        s"MongoScala3Codec cannot derive a codec for '$typeName': it is $kind, not a case class." +
          "\n\nDerivation reads a case class's primary constructor to decide which fields to write, and" +
          s" '$typeName' has no such constructor to read." +
          remediation
      )
    end if

    // Note: we intentionally use the runtime `registry` passed to `get` for nested lookups,
    // so multiple providers registered together can resolve each other.
    '{
      new CodecProvider:
        /** Returns a Codec for the given class if it matches the expected type. The unchecked cast is safe because we verify runtimeClass
          * compatibility.
          */
        @SuppressWarnings(Array("unchecked"))
        def get[C](clazz: Class[C], registry: CodecRegistry): Codec[C] =
          if $classTag.runtimeClass.isAssignableFrom(clazz) then generateCodec[T]($config, registry)(using $classTag).asInstanceOf[Codec[C]]
          else null
    }
  end createCodecProviderImpl

end CodecProviderMacro
