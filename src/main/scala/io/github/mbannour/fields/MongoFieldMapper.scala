package io.github.mbannour.fields

import io.github.mbannour.bson.macros.AnnotationName.extractAnnotationMap

import scala.deriving.*
import scala.compiletime.*

trait MongoFieldResolver:
  def extract(prefix: String = ""): List[(String, String)]

object MongoFieldMapper:
  private val cache = scala.collection.concurrent.TrieMap.empty[String, Map[String, String]]

  inline def asMap[T](using m: Mirror.Of[T]): Map[String, String] =
    val className = constValue[m.MirroredLabel]
    cache.getOrElseUpdate(className, apply[T].extract().toMap)

  inline def mongoField[T](using m: Mirror.Of[T]): Map[String, String] =
    val className = constValue[m.MirroredLabel]
    cache.getOrElseUpdate(className, apply[T].extract().toMap)

  object AnnotationMap:
    inline def forType[T]: Map[String, String] = extractAnnotationMap[T]

  inline def apply[T](using m: Mirror.Of[T]): MongoFieldResolver =
    val overrides: Map[String, String] = AnnotationMap.forType[T]

    (prefix: String) =>
      val labels = getLabels[T]
      val extractors = getExtractors[T]
      labels.zip(extractors).flatMap { (label, extractor) =>

        val renamed = overrides.getOrElse(label, label)
        val fullKeyRaw = if prefix.isEmpty then renamed else s"$prefix.$renamed"
        val fullKey = normalizePath(fullKeyRaw)
        extractor.extract(fullKey)
      }
  end apply

  /** Normalize paths by removing `.Some.` and optionally `.value.` */
  private def normalizePath(path: String): String =
    path.replace(".Some.", ".").replace(".value.", ".")

  private inline def getLabels[T](using m: Mirror.Of[T]): List[String] =
    getLabelsImpl[m.MirroredElemLabels]

  private inline def getLabelsImpl[Labels <: Tuple]: List[String] =
    inline erasedValue[Labels] match
      case _: (h *: t)   => constValue[h].toString :: getLabelsImpl[t]
      case _: EmptyTuple => Nil

  private inline def getExtractors[T](using m: Mirror.Of[T]): List[MongoFieldResolver] =
    getExtractorsImpl[m.MirroredElemTypes]

  private inline def getExtractorsImpl[Elems <: Tuple]: List[MongoFieldResolver] =
    inline erasedValue[Elems] match
      case _: (h *: t) =>
        val head: MongoFieldResolver = summonFrom {
          case _: Mirror.Of[`h`] => apply[`h`]
          case _                 => DefaultMongoFieldResolver
        }
        head :: getExtractorsImpl[t]
      case _: EmptyTuple => Nil
end MongoFieldMapper

private object DefaultMongoFieldResolver extends MongoFieldResolver:

  def extract(prefix: String): List[(String, String)] = List(prefix -> prefix)
