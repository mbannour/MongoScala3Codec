package io.github.mbannour.bson.macros

import io.github.mbannour.bson.macros.AnnotationName.findAnnotationValue

import scala.quoted.*
import scala.reflect.ClassTag

object CaseClassMapper:

  private[mbannour] inline def caseClassMap[T]: Map[String, Class[?]] = ${ caseClassMapImpl[T] }

  private[mbannour] def caseClassMapImpl[T: Type](using Quotes): Expr[Map[String, Class[?]]] =

    import quotes.reflect.*

    val mainType = TypeRepr.of[T]
    val mainSymbol = mainType.typeSymbol

    def isSealed(symbol: Symbol): Boolean = symbol.isClassDef && symbol.flags.is(Flags.Sealed)

    def isCaseClass(symbol: Symbol): Boolean = symbol.isClassDef && symbol.flags.is(Flags.Case)

    def subclasses(symbol: Symbol): Set[Symbol] =
      val directSubclasses = symbol.children.toSet
      directSubclasses ++ directSubclasses.flatMap(subclasses)

    val caseClassSymbols =
      val knownTypes = if isSealed(mainSymbol) then subclasses(mainSymbol) + mainSymbol else Set(mainSymbol)
      knownTypes.filter(isCaseClass)

    if caseClassSymbols.isEmpty && isSealed(mainSymbol) then
      val kind = if mainSymbol.flags.is(Flags.Trait) then "trait" else "class"
      report.errorAndAbort(s"No known subclasses of the sealed $kind '${mainSymbol.name}'.")

    def simpleClassName(fullName: String): String =
      fullName
        .split('.')
        .lastOption
        .getOrElse(fullName)
        .replaceAll("^_+", "")
        .replaceAll("\\$\\d+", "")
        .replaceAll("\\$+", "")

    val caseClassEntries: List[Expr[(String, Class[?])]] = caseClassSymbols.collect {
      case symbol if symbol.typeRef.classSymbol.isDefined =>
        val className = Expr(simpleClassName(symbol.fullName))
        val res: Expr[Option[String]] = findAnnotationValue[T](className)

        val name: Expr[String] = res match
          case '{ Some($annotationValue: String) } => annotationValue
          case '{ None }                           => Expr(simpleClassName(symbol.fullName))

        symbol.typeRef.asType match
          case '[tType] =>
            Expr.summon[ClassTag[tType]] match
              case Some(classTag) => '{ $name -> $classTag.runtimeClass }
              case None           => report.errorAndAbort(s"Cannot summon ClassTag for ${symbol.fullName}.")
    }.toList

    '{ Map[String, Class[?]](${ Varargs(caseClassEntries) }*) }
  end caseClassMapImpl
end CaseClassMapper
