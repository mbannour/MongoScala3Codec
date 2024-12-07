package io.github.mbannour.bson.macros

import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AnnotationNameSpec extends AnyFunSuite with Matchers {


  test("findAnnotationValue should return the annotation value for a field with @BsonProperty") {
    case class Example(@BsonProperty("aliasForFoo") foo: String, bar: Int)
    val result = AnnotationName.invokeFindAnnotationValue[Example]("foo")
    result shouldBe Some("aliasForFoo")
  }


  test("findAnnotationValue should return None for a field without @BsonProperty") {
    case class Example(@BsonProperty("aliasForFoo") foo: String, bar: Int)
    val result = AnnotationName.invokeFindAnnotationValue[Example]("bar")
    result shouldBe None
  }


  test("findAnnotationValue should return None for a non-existent field") {
    case class Example(@BsonProperty("aliasForFoo") foo: String, bar: Int)
    val result = AnnotationName.invokeFindAnnotationValue[Example]("baz")
    result shouldBe None
  }


  test("findAnnotationValue should return None when no fields have annotations") {
    case class Example(foo: String, bar: Int)
    val result = AnnotationName.invokeFindAnnotationValue[Example]("foo")
    result shouldBe None
  }


  test("findAnnotationValue should return None for an empty case class") {
    case class EmptyCaseClass()
    val result = AnnotationName.invokeFindAnnotationValue[EmptyCaseClass]("foo")
    result shouldBe None
  }


  test("findAnnotationValue should handle fields with multiple annotations") {
    case class Example(@BsonProperty("aliasForFoo") foo: String, bar: Int)
    val result = AnnotationName.invokeFindAnnotationValue[Example]("foo")
    result shouldBe Some("aliasForFoo")
  }
}

