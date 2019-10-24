package lift.core

import lift.core.types._
import lift.core.primitives._
import lift.core.traversal._
import lift.core.DSL._

import scala.collection.mutable

class traverse extends test_util.Tests {
  val e = nFun(h => nFun(w => fun(ArrayType(h, ArrayType(w, float)))(input =>
    map(map(fun(x => x)))(input)
  )))

  class TraceVisitor(var trace: mutable.ArrayBuffer[Any]) extends Visitor
  {
    override def visitExpr(e: Expr): Result[Expr] = {
      println(e)
      trace += e
      Continue(e, this)
    }

    override def visitNat(ae: Nat): Result[Nat] = {
      println(ae)
      trace += ae
      Continue(ae, this)
    }

    override def visitType[T <: Type](t: T): Result[T] = {
      println(t)
      trace += t
      Continue(t, this)
    }
  }

  test("traverse an expression depth-first") {
    val expected = {
      Seq(
        { case _: DepLambda[NatKind]@unchecked => () },
        { case _: NatIdentifier => () },
        { case _: DepLambda[NatKind]@unchecked => () },
        { case _: NatIdentifier => () },
        { case _: Lambda => () },
        { case _: Apply => () },
        { case _: Apply => () },
        { case _: Map => () },
        { case _: Apply => () },
        { case _: Map => () },
        { case _: Lambda => () },
        { case _: Identifier => () },
        { case _: Identifier => () },
        { case ArrayType(_, ArrayType(_, _: ScalarType)) => () }
      ) : Seq[Any => Unit]
    }

    val trace = mutable.ArrayBuffer[Any]()
    val result = DepthFirstLocalResult(e, new TraceVisitor(trace))

    // the expression should not have changed
    assert(result == e)
    // the trace should match expectations
    trace.length shouldBe expected.length
    trace.zip(expected).foreach({ case (x, e) => e(x) })
  }

/* TODO?
  test("traverse an expression depth-first with types") {
  }
*/

  test("traverse an expression depth-first with stop and update") {
    val expected = {
      Seq(
        { case _: DepLambda[NatKind]@unchecked => () },
        { case _: NatIdentifier => () },
        { case _: DepLambda[NatKind]@unchecked => () },
        { case _: NatIdentifier => () },
        { case _: Lambda => () }
      ) : Seq[Any => Unit]
    }

    val trace = mutable.ArrayBuffer[Any]()
    class Visitor extends TraceVisitor(trace) {
      override def visitExpr(expr: Expr): Result[Expr] = {
        expr match {
          case Apply(Apply(Map(), _), e) =>
            val r = `apply`(fun(x => x), e)
            println(r)
            Stop(r)
          case _ => super.visitExpr(expr)
        }
      }
    }

    val result = DepthFirstGlobalResult(e, new Visitor)

    // the expression should have changed
    result match {
      case traversal.Stop(r) =>
        assert(r ==
          nFun(h => nFun(w => fun(ArrayType(h, ArrayType(w, float)))(input =>
            `apply`(fun(x => x), input)
          )))
        )
      case _ => throw new Exception("the traversal should have stopped")
    }
    // the trace should match expectations
    trace.length shouldBe expected.length
    trace.zip(expected).foreach({ case (x, e) => e(x) })
  }

  test("traverse an expression depth-first with global stop") {
    val e = nFun(n => fun(ArrayType(n, float))(input =>
      input |> map(fun(x => x)) |> map(fun(x => x))
    ))

    class Visitor extends traversal.Visitor {
      override def visitExpr(expr: Expr): Result[Expr] = {
        expr match {
          case Apply(Map(), f) =>
            println(f)
            Stop(f)
          case _ => Continue(expr, this)
        }
      }
    }

    val result = DepthFirstGlobalResult(e, new Visitor)

    // the expression should have changed
    (result: @unchecked) match {
      case traversal.Stop(r) =>
        val expected = nFun(n => fun(ArrayType(n, float))(input => {
          val x = identifier(freshName("x"))
          `apply`(lambda(x, x), input |> map(fun(x => x)))
        }))
        assert(r == expected)
    }
  }
}
