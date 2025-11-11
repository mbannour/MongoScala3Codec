package io.github.mbannour.mongo.codecs

import scala.quoted.*
import scala.reflect.ClassTag
import scala.annotation.unused

import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

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

    val mainType = TypeRepr.of[T]
    val mainTypeSymbol = mainType.typeSymbol

    // Validate that T is a case class
    val typeName = mainTypeSymbol.name
    if !mainTypeSymbol.flags.is(Flags.Case) then
      report.errorAndAbort(
        s"Cannot create codec provider for '$typeName'" +
        "\n\n'$typeName' is not a case class." +
        "\n\nSuggestion: Declare it as a case class:" +
        s"\n  case class $typeName(...)"
      )

    // Warn if the type is abstract or a trait
    if mainTypeSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(
        s"Cannot create codec provider for '$typeName'" +
        "\n\n'$typeName' is a trait. Only concrete case classes can have codec providers." +
        "\n\nSuggestion: For sealed trait hierarchies, register each concrete case class implementation:" +
        s"\n  sealed trait $typeName" +
        s"\n  case class SubType1(...) extends $typeName" +
        s"\n  case class SubType2(...) extends $typeName" +
        "\n\n  // Then register each:" +
        "\n  val registry = RegistryBuilder" +
        "\n    .from(...)"+
        "\n    .register[SubType1]" +
        "\n    .register[SubType2]" +
        "\n    .build"
      )

    if mainTypeSymbol.flags.is(Flags.Abstract) then
      report.errorAndAbort(
        s"Cannot create codec provider for '$typeName'" +
        "\n\n'$typeName' is an abstract class. Only concrete case classes are supported." +
        "\n\nSuggestion: Make it a concrete case class or create codec providers for its concrete subclasses."
      )

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
