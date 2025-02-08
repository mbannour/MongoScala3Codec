package io.github.mbannour.mongo.codecs

import CaseClassCodecGenerator.generateCodec
import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

import scala.quoted.*
import scala.reflect.ClassTag

/** Provides macros for generating a MongoDB {@link org.bson.codecs.configuration.CodecProvider CodecProvider} for a given case class type.
  *
  * This object contains inline methods that create a CodecProvider tailored for case classes. The generated CodecProvider will supply a
  * Codec for the case class type `T` using the provided {@link org.bson.codecs.configuration.CodecRegistry CodecRegistry} . Depending on
  * the method invoked, the Codec will either ignore or encode fields with a value of `None`.
  *
  * @see
  *   [[CaseClassCodecGenerator.generateCodec]]
  */

object CodecProviderMacro:

  /** Creates a CodecProvider for the case class type `T` that **ignores** fields with a value of `None`.
    *
    * The generated Codec will not include fields set to `None` during the encoding process.
    */
  inline def createCodecProviderIgnoreNone[T](inline codecRegistry: CodecRegistry)(using classTag: ClassTag[T]): CodecProvider =
    createCodecProvider[T](false, codecRegistry)

  /** Creates a CodecProvider for the case class type `T` that **encodes** fields with a value of `None`.
    *
    * The generated Codec will include fields set to `None` during the encoding process.
    */
  inline def createCodecProviderEncodeNone[T](inline codecRegistry: CodecRegistry)(using classTag: ClassTag[T]): CodecProvider =
    createCodecProvider[T](true, codecRegistry)

  private inline def createCodecProvider[T](inline encodeNone: Boolean, codecRegistry: CodecRegistry)(using
      inline classTag: ClassTag[T]
  ) =
    ${ createCodecProviderImpl[T]('encodeNone, 'codecRegistry, 'classTag) }

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

    val codecExpr = '{
      generateCodec[T]($encodeNone, $codecRegistry)(using $classTag)
    }
    '{
      new CodecProvider:
        @SuppressWarnings(Array("unchecked"))
        def get[C](clazz: Class[C], registry: CodecRegistry): Codec[C] =
          if $classTag.runtimeClass.isAssignableFrom(clazz) then $codecExpr.asInstanceOf[Codec[C]]
          else null
    }
  end createCodecProviderImpl
end CodecProviderMacro
