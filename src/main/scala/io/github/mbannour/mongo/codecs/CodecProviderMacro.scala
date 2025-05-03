package io.github.mbannour.mongo.codecs

import CaseClassCodecGenerator.generateCodec
import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

import scala.quoted.*
import scala.reflect.ClassTag

/** `CodecProviderMacro` is a utility object that provides inline macros for generating MongoDB `CodecProvider` instances for Scala case
  * classes.
  *
  * A `CodecProvider` wraps a generated `Codec[T]` so it can be plugged into the MongoDB driver’s `CodecRegistry`, allowing seamless
  * serialization and deserialization of your domain models.
  *
  * Two variants are supported:
  *   - `createCodecProviderEncodeNone` encodes `None` fields as BSON `null`.
  *   - `createCodecProviderIgnoreNone` omits `None` fields entirely from the document.
  *
  * ==Quick Start==
  * In your case class companion: {{ import io.github.mbannour.mongo.codecs.CodecProviderMacro import
  * org.bson.codecs.configuration.CodecRegistries import org.mongodb.scala.MongoClient
  *
  * case class Person(name: String, age: Int, nickname: Option[String])
  *
  * object Person: // Choose encode-or-ignore None private val provider = CodecProviderMacro.createCodecProviderEncodeNone[Person]
  *
  * given registry: CodecRegistry = CodecRegistries.fromRegistries( MongoClient.DEFAULT_CODEC_REGISTRY,
  * CodecRegistries.fromProviders(provider) ) end Person }}
  *
  * Then attach this registry to your database: {{ val client = MongoClient("mongodb://localhost") val db =
  * client.getDatabase("mydb").withCodecRegistry(summon) val coll = db.getCollection[Person]("people") coll.insertOne(Person("Alice", 30,
  * None)).toFuture() }}
  *
  * @see
  *   [[CaseClassCodecGenerator.generateCodec]] for the underlying codec generator.
  */
object CodecProviderMacro:

  /** Creates a `CodecProvider` for type `T` that **ignores** `None` values during serialization.
    *
    * @tparam T
    *   The case class type for which to generate the provider.
    * @param classTag
    *   Runtime `ClassTag` for `T` (injected implicitly).
    * @param codecRegistry
    *   The base `CodecRegistry` used for nested type lookups (e.g., `String`, `Int`, other case classes).
    * @return
    *   A `CodecProvider` that will supply a BSON `Codec[T]` omitting fields with `None` values.
    *
    * @example
    *   {{ val provider = CodecProviderMacro.createCodecProviderIgnoreNone[Person] }}
    */
  inline def createCodecProviderIgnoreNone[T](using
      classTag: ClassTag[T],
      codecRegistry: CodecRegistry
  ): CodecProvider =
    createCodecProvider[T](false, codecRegistry)

  /** Creates a `CodecProvider` for type `T` that **encodes** `None` values as BSON `null`.
    *
    * @tparam T
    *   The case class type for which to generate the provider.
    * @param classTag
    *   Runtime `ClassTag` for `T` (injected implicitly).
    * @param codecRegistry
    *   The base `CodecRegistry` used for nested type lookups.
    * @return
    *   A `CodecProvider` that will supply a BSON `Codec[T]` including `None` fields.
    *
    * @example
    *   {{ val provider = CodecProviderMacro.createCodecProviderEncodeNone[Person] }}
    */
  inline def createCodecProviderEncodeNone[T](using
      classTag: ClassTag[T],
      codecRegistry: CodecRegistry
  ): CodecProvider =
    createCodecProvider[T](true, codecRegistry)

  /** Internal macro that generates a `CodecProvider` for `T` based on the `encodeNone` flag.
    *
    * @param encodeNone
    *   `true` to encode `None` as `null`, `false` to omit `None` fields.
    * @param codecRegistry
    *   Base `CodecRegistry` for nested lookups.
    * @tparam T
    *   The case class type.
    * @return
    *   A `CodecProvider` implementing the logic above.
    */
  private inline def createCodecProvider[T](
      inline encodeNone: Boolean,
      codecRegistry: CodecRegistry
  )(using inline classTag: ClassTag[T]) =
    ${ createCodecProviderImpl[T]('encodeNone, 'codecRegistry, 'classTag) }

  /** Macro implementation: emits a `new CodecProvider { ... }` that delegates to the `CaseClassCodecGenerator.generateCodec` method at
    * compile time.
    */
  private def createCodecProviderImpl[T: Type](
      encodeNone: Expr[Boolean],
      codecRegistry: Expr[CodecRegistry],
      classTag: Expr[ClassTag[T]]
  )(using Quotes) =
    import quotes.reflect.*

    val mainType = TypeRepr.of[T]
    val mainTypeSymbol = mainType.typeSymbol

    if !mainTypeSymbol.flags.is(Flags.Case) then
      report.errorAndAbort(s"${mainTypeSymbol.name} is not a case class and cannot be used as a Codec.")

    val codecExpr = '{ generateCodec[T]($encodeNone, $codecRegistry)(using $classTag) }
    '{
      new CodecProvider:
        @SuppressWarnings(Array("unchecked"))
        def get[C](clazz: Class[C], registry: CodecRegistry): Codec[C] =
          if $classTag.runtimeClass.isAssignableFrom(clazz) then $codecExpr.asInstanceOf[Codec[C]]
          else null
    }
  end createCodecProviderImpl

end CodecProviderMacro
