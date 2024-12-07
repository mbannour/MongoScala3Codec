package io.github.mbannour.bson.macros

import scala.quoted.*
import scala.reflect.ClassTag

object ClassToCaseFlagMap:

  inline def classToCaseClassMap[T]: Map[Class[?], Boolean] = ${ classToCaseClassMapImpl[T] }

  def classToCaseClassMapImpl[T: Type](using q: Quotes): Expr[Map[Class[?], Boolean]] =

    import quotes.reflect.*

    val primitiveTypesMap: Map[TypeRepr, TypeRepr] = Map(
      TypeRepr.of[Boolean] -> TypeRepr.of[java.lang.Boolean],
      TypeRepr.of[Byte] -> TypeRepr.of[java.lang.Byte],
      TypeRepr.of[Char] -> TypeRepr.of[java.lang.Character],
      TypeRepr.of[Double] -> TypeRepr.of[java.lang.Double],
      TypeRepr.of[Float] -> TypeRepr.of[java.lang.Float],
      TypeRepr.of[Int] -> TypeRepr.of[java.lang.Integer],
      TypeRepr.of[Long] -> TypeRepr.of[java.lang.Long],
      TypeRepr.of[Short] -> TypeRepr.of[java.lang.Short]
    )

    def isCaseClassOrSealed(tpe: TypeRepr): Boolean =
      tpe.typeSymbol.isClassDef && (tpe.typeSymbol.flags.is(Flags.Case) || tpe.typeSymbol.flags.is(Flags.Sealed))

    def collectFields(tpe: TypeRepr): List[TypeRepr] =
      val paramTypes = tpe.typeSymbol.primaryConstructor.paramSymss.flatten.collect {
        case sym if sym.isTerm && sym.isValDef =>
          sym.termRef.asType match
            case '[f] => TypeRepr.of[f]
      }
      paramTypes.flatMap { fieldType =>
        if isCaseClassOrSealed(fieldType) then collectFields(fieldType) :+ fieldType
        else List(fieldType)
      }
    end collectFields

    def flattenTypeArgs(tpe: TypeRepr): List[TypeRepr] =
      val typeArgs = tpe match
        case AppliedType(_, args) => args
        case _                    => List.empty

      val types = tpe +: typeArgs.flatMap(flattenTypeArgs)
      types.map(t => primitiveTypesMap.getOrElse(t, t))

    def getFieldTypes(tpe: TypeRepr): List[TypeRepr] =
      collectFields(tpe) :+ tpe

    val flattenedFieldTypes: List[TypeRepr] =
      val mainType = TypeRepr.of[T]
      getFieldTypes(mainType).flatMap(t => flattenTypeArgs(t))

    val classToCaseClassEntries: List[Expr[(Class[?], Boolean)]] = flattenedFieldTypes.distinct.map { tpe =>
      tpe.asType match
        case '[tType] =>
          Expr.summon[ClassTag[tType]] match
            case Some(ct) =>
              val classExpr = '{ $ct.runtimeClass.asInstanceOf[Class[?]] }
              val isCaseClassExpr = Expr(isCaseClassOrSealed(tpe))
              '{ $classExpr -> $isCaseClassExpr }
            case None =>
              report.errorAndAbort(s"Cannot summon ClassTag for type: ${tpe.show}")
    }

    val mapEntriesExpr = Varargs(classToCaseClassEntries)
    '{ Map[Class[?], Boolean](${ mapEntriesExpr }*) }
  end classToCaseClassMapImpl
end ClassToCaseFlagMap
