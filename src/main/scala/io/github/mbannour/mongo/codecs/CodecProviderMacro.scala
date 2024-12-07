package io.github.mbannour.mongo.codecs

import io.github.mbannour.bson.macros.CaseClassCodecGenerator.generateCodec
import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

import scala.quoted.*
import scala.reflect.ClassTag

object CodecProviderMacro:

  inline def createCodecProvider[T](inline codecRegistry: CodecRegistry, encodeNone: Boolean)(using
      inline classTag: ClassTag[T]
  ): CodecProvider =
    ${ createCodecProviderImpl[T]('codecRegistry, 'encodeNone, 'classTag) }

  private def createCodecProviderImpl[T: Type](
      codecRegistry: Expr[CodecRegistry],
      encodeNone: Expr[Boolean],
      classTag: Expr[ClassTag[T]]
  )(using Quotes): Expr[CodecProvider] =
    import quotes.reflect.*

    val mainType = TypeRepr.of[T]
    val mainTypeSymbol = mainType.typeSymbol

    if !mainTypeSymbol.flags.is(Flags.Case) then
      report.errorAndAbort(s"${mainTypeSymbol.name} is not a case class and cannot be used as a Codec.")

    val codecExpr = '{ generateCodec[T]($codecRegistry, $encodeNone)(using $classTag) }

    '{
      new CodecProvider:
        @SuppressWarnings(Array("unchecked"))
        def get[C](clazz: Class[C], registry: CodecRegistry): Codec[C] =
          if $classTag.runtimeClass.isAssignableFrom(clazz) then $codecExpr.asInstanceOf[Codec[C]]
          else null
    }
  end createCodecProviderImpl
end CodecProviderMacro
