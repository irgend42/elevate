package elevate.core

import elevate.core.rules._
import elevate.core.rules.movement._
import elevate.core.strategies._
import elevate.core.strategies.traversal._
import _root_.lift.core.{Expr, Identifier, StructuralEquality}
import _root_.lift.core.primitives._
import _root_.lift.core.DSL._
import scala.language.implicitConversions

class movement extends idealised.util.Tests {

  implicit def rewriteResultToExpr(r: RewriteResult): Expr = {
    r match { case Success(e) => e }
  }

  val norm: Strategy = normalize(betaReduction <+ etaReduction)
  def eq(a: Expr, b: Expr): Boolean = StructuralEquality(norm(a), norm(b))

  // notation
  def T: Expr = transpose
  def S: Expr = split(4)//slide(3)(1)
  def J: Expr = join
  def *(x: Expr): Expr = map(x)
  def **(x: Expr): Expr = map(map(x))
  def ***(x: Expr): Expr = map(map(map(x)))
  def λ(f: Identifier => Expr): Expr = fun(f)

  // transpose

  test("**f >> T -> T >> **f") {
    val gold = λ(f => T >> **(f))

    assert(
      List(
        norm(λ(f => *(λ(x => *(f)(x))) >> T)).get,
        λ(f => **(f) >> T)
      ).forall((expr: Expr) =>
        eq(oncetd(`**f >> T -> T >> **f`)(expr), gold))
    )
  }

  test("**f >> T -> T >> **f - family") {
    assert(eq(
      oncetd(lift(`T >> **f -> **f >> T`))(λ(f => *(T) >> ***(f))),
      λ(f => ***(f) >> *(T)))
    )
  }

  test("T >> **f -> **f >> T") {
    assert(eq(
      oncetd(`T >> **f -> **f >> T`)(λ(f => T >> **(f))),
      λ(f => **(f) >> T))
    )
  }

  // split/slide

  test("S >> **f -> *f >> S") {
    assert(eq(
      oncetd(`S >> **f -> *f >> S`)(λ(f => S >> **(f))),
      λ(f => *(f) >> S))
    )
  }

  test("*f >> S -> S >> **f") {
    assert(eq(
      oncetd(`*f >> S -> S >> **f`)(λ(f => *(f) >> S)),
      λ(f => S >> **(f)))
    )
  }

  // join

  test("J >> *f -> **f >> J") {
    assert(eq(
      oncetd(`J >> *f -> **f >> J`)(λ(f => J >> *(f))),
      λ(f => **(f) >> J)
    ))
  }

  test("**f >> J -> *f >> J") {
    assert(eq(
      oncetd(`**f >> J -> J >> *f`)(λ(f => **(f) >> J)),
      λ(f => J >> *(f))
    ))
  }

  // special-cases

  test("T >> S -> *S >> T >> *T") {
    assert(eq(
      oncetd(`T >> S -> *S >> T >> *T`)(T >> S),
      *(S) >> T >> *(T)
    ))
  }

  test("T >> *S -> S >> *T >> T") {
    assert(eq(
      oncetd(`T >> *S -> S >> *T >> T`)(T >> *(S)),
      S >> *(T) >> T
    ))
  }

  test("*S >> T -> T >> S >> *T") {
    assert(eq(
      oncetd(`*S >> T -> T >> S >> *T`)(*(S) >> T),
      T >> S >> *(T)
    ))
  }

  test("J >> T -> *T >> T >> *J") {
    assert(eq(
      oncetd(`J >> T -> *T >> T >> *J`)(J >> T),
      *(T) >> T >> *(J)
    ))
  }

  test("T >> *J -> *T >> J >> T") {
    assert(eq(
      oncetd(`T >> *J -> *T >> J >> T`)(T >> *(J)),
      *(T) >> J >> T
    ))
  }

  test("*T >> J -> T >> *J >> T") {
    assert(eq(
      oncetd(`*T >> J -> T >> *J >> T`)(*(T) >> J),
      T >> *(J) >> T
    ))
  }

  test("*J >> T -> T >> *T >> J") {
    assert(eq(
      oncetd(`*J >> T -> T >> *T >> J`)(*(J) >> T),
      T >> *(T) >> J
    ))
  }

  test("J >> J -> *J >> J") {
    assert(eq(
      oncetd(`J >> J -> *J >> J`)(J >> J),
      *(J) >> J
    ))
  }

  test("*J >> J -> J >> J") {
    assert(eq(
      oncetd(`*J >> J -> J >> J`)(*(J) >> J),
      J >> J
    ))
  }

  test("slideOverSplit") {
    assert(eq(
      oncetd(slideBeforeSplit)(slide(3)(1) >> split(16)),
      slide(16+3-1)(16) >> map(slide(3)(1))
    ))
  }
}
