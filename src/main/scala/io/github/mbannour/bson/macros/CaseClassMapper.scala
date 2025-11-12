package io.github.mbannour.bson.macros

import scala.quoted.*
import scala.reflect.ClassTag

import io.github.mbannour.bson.macros.AnnotationName.findAnnotationValue

/** Provides macros for mapping a discriminator (simple class name) to the runtime Class of a case class. This is especially useful for
  * sealed hierarchies where a custom annotation may override the default name.
  */
object CaseClassMapper:

  /** Macro that generates a Map[String, Class[?]] for type `T` and its case class subclasses.
    *
    * The keys in the map are simple class names (optionally overridden via an annotation), and the values are the corresponding runtime
    * classes.
    */
  private[mbannour] inline def caseClassMap[T]: Map[String, Class[?]] =
    ${ caseClassMapImpl[T] }

  private[mbannour] def caseClassMapImpl[T: Type](using Quotes): Expr[Map[String, Class[?]]] =
    import quotes.reflect.*

    val mainType = TypeRepr.of[T]
    val mainSymbol = mainType.typeSymbol

    /** Returns true if the given symbol is sealed. */
    def isSealed(symbol: Symbol): Boolean =
      symbol.isClassDef && symbol.flags.is(Flags.Sealed)

    /** Returns true if the given symbol represents a case class. */
    def isCaseClass(symbol: Symbol): Boolean =
      symbol.isClassDef && symbol.flags.is(Flags.Case)

    /** Recursively collects all subclasses of a sealed symbol. */
    def subclasses(symbol: Symbol): Set[Symbol] =
      val directSubclasses = symbol.children.toSet
      directSubclasses ++ directSubclasses.flatMap(subclasses)

    // Determine all candidate symbols: if the main symbol is sealed, include its subclasses; otherwise, use the main symbol.
    val knownTypes: Set[Symbol] =
      if isSealed(mainSymbol) then subclasses(mainSymbol) + mainSymbol
      else Set(mainSymbol)

    // Filter only those symbols that are case classes.
    val caseClassSymbols: Set[Symbol] = knownTypes.filter(isCaseClass)

    if caseClassSymbols.isEmpty && isSealed(mainSymbol) then
      val kind = if mainSymbol.flags.is(Flags.Trait) then "trait" else "class"
      val typeName = mainSymbol.name
      val allSubclasses = subclasses(mainSymbol)
      val subclassInfo = if allSubclasses.nonEmpty then
        val names = allSubclasses.map(_.name).mkString(", ")
        s"\n\nFound subclasses: $names (but none are case classes)"
      else "\n\nNo subclasses found at all."

      report.errorAndAbort(
        s"Cannot generate codec for sealed $kind '$typeName'" +
          subclassInfo +
          "\n\nSuggestion:" +
          s"\n  • Ensure all subclasses of '$typeName' are case classes" +
          s"\n  • Example:\n" +
          s"      sealed trait $typeName\n" +
          s"      case class SubType1(...) extends $typeName\n" +
          s"      case class SubType2(...) extends $typeName"
      )
    end if

    /** Simplifies a fully-qualified class name by extracting the simple name and removing compiler-generated artifacts.
      */
    def simpleClassName(fullName: String): String =
      fullName
        .split('.')
        .lastOption
        .getOrElse(fullName)
        .replaceAll("^_+", "")
        .replaceAll("\\$\\d+", "")
        .replaceAll("\\$+", "")

    // Build an expression for each case class entry: (name, runtimeClass)
    val caseClassEntries: List[Expr[(String, Class[?])]] =
      caseClassSymbols.toList.collect {
        case symbol if symbol.typeRef.classSymbol.isDefined =>
          // Compute the default simple name.
          val defaultName = simpleClassName(symbol.fullName)
          val nameExpr: Expr[String] =
            findAnnotationValue[T](Expr(defaultName)) match
              case '{ Some($annotatedName: String) } => annotatedName
              case '{ None }                         => Expr(defaultName)

          symbol.typeRef.asType match
            case '[tType] =>
              Expr.summon[ClassTag[tType]] match
                case Some(classTagExpr) =>
                  '{ $nameExpr -> $classTagExpr.runtimeClass }
                case None =>
                  report.errorAndAbort(s"Cannot summon ClassTag for ${symbol.fullName}.")
      }

    '{ Map[String, Class[?]](${ Varargs(caseClassEntries) }*) }
  end caseClassMapImpl
end CaseClassMapper
