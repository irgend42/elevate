package idealised.OpenMP.CodeGeneration

import idealised._
import idealised.C.AST.{ArraySubscript, Assignment, Decl}
import idealised.C.CodeGeneration.{CodeGenerator => CCodeGenerator}
import idealised.C.CodeGeneration.CodeGenerator.CIntExpr
import idealised.DPIA.DSL._
import idealised.DPIA.FunctionalPrimitives.{AsScalar, AsVector, ForeignFunction}
import idealised.DPIA.ImperativePrimitives.{AsScalarAcc, AsVectorAcc, ForVec}
import idealised.DPIA.Phrases._
import idealised.DPIA.Semantics.OperationalSemantics
import idealised.DPIA.Semantics.OperationalSemantics.{ArrayData, VectorData}
import idealised.DPIA.Types.{AccType, CommandType, DataType, ExpType, PhraseType, ScalarType, VectorType}
import idealised.DPIA.{Nat, NatIdentifier, Phrases, error, freshName}
import idealised.OpenMP.ImperativePrimitives.{ParFor, ParForNat}
import lift.arithmetic
import lift.arithmetic._

import scala.collection.immutable.VectorBuilder
import scala.collection.{immutable, mutable}

object CodeGenerator {
  def apply(): CodeGenerator =
    new CodeGenerator(mutable.ListBuffer[Decl](), immutable.Map[String, arithmetic.Range]())
}

class CodeGenerator(override val decls: CCodeGenerator.Declarations,
                    override val ranges: CCodeGenerator.Ranges)
  extends CCodeGenerator(decls, ranges)
{
  override def name: String = "OpenMP"

  override def updatedRanges(key: String, value: lift.arithmetic.Range): CodeGenerator =
    new CodeGenerator(decls, ranges.updated(key, value))


  override def cmd(phrase: Phrase[CommandType], env: Environment): Stmt = {
    phrase match {
      case ParFor(n, dt, a, Lambda(i, Lambda(o, p))) => OpenMPCodeGen.codeGenParFor(n, dt, a, i, o, p, env)
      case ForVec(n, dt, a, Lambda(i, Lambda(o, p))) => OpenMPCodeGen.codeGenParForVec(n, dt, a, i, o, p, env)
      case ParForNat(n, _, a, NatDependentLambda(i, Lambda(o, p))) =>
        OpenMPCodeGen.codeGenParForNat(n, a, i, o, p, env)
      case _ => super.cmd(phrase, env)
    }
  }

  override def acc(phrase: Phrase[AccType],
                   env: Environment,
                   path: Path,
                   cont: Expr => Stmt): Stmt = {
    phrase match {
      case AsVectorAcc(n, _, _, a) => path match {
        case (i : CIntExpr) :: ps =>     acc(a, env, CIntExpr(i / n) :: ps, cont)
        case _ =>           error(s"Expected path to be not empty")
      }
      case AsScalarAcc(_, m, dt, a) => path match {
        case (i : CIntExpr) :: (j : CIntExpr) :: ps =>
          acc(a, env, CIntExpr((i * m) + j) :: ps, cont)

        case (i : CIntExpr) :: Nil =>
          acc(a, env, CIntExpr(i * m) :: Nil, {
            case ArraySubscript(v, idx) =>
              // emit something like: ((struct float4 *)v)[idx]
              val ptrType = C.AST.PointerType(typ(VectorType(m, dt)))
              cont( C.AST.ArraySubscript(C.AST.Cast(ptrType, v), idx) )
          })
        case _ =>           error(s"Expected path to be not empty")
      }
      case _ =>             super.acc(phrase, env, path, cont)
    }
  }

  override def exp(phrase: Phrase[ExpType],
                   env: Environment,
                   path: Path,
                   cont: Expr => Stmt): Stmt = {
    phrase match {
      case Phrases.Literal(n) => (path, n.dataType) match {
        case (Nil, _: VectorType)       => cont(OpenMPCodeGen.codeGenLiteral(n))
        case ((i : CIntExpr) :: Nil, _: VectorType) =>
          cont(C.AST.ArraySubscript(OpenMPCodeGen.codeGenLiteral(n), C.AST.ArithmeticExpr(i)))
        case _ => super.exp(phrase, env, path, cont)
      }
      case UnaryOp(op, e) => phrase.t.dataType match {
        case _: VectorType => path match {
          case i :: ps => exp(e, env, i :: ps, e => cont(CCodeGen.codeGenUnaryOp(op, e)))
          case _ => error(s"Expected path to be not empty")
        }
        case _ => super.exp(phrase, env, path, cont)
      }
      case BinOp(op, e1, e2) => phrase.t.dataType match {
        case _: VectorType => path match {
          case i :: ps =>
            exp(e1, env, i :: ps, e1 =>
              exp(e2, env, i :: ps, e2 =>
                cont(CCodeGen.codeGenBinaryOp(op, e1, e2))))
          case _ => error(s"Expected path to be not empty")
        }
        case _ => super.exp(phrase, env, path, cont)
      }
      case ForeignFunction(f, inTs, outT, args) =>
        OpenMPCodeGen.codeGenForeignFunction(f, inTs, outT, args, env, path, cont)
      case AsVector(n, _, dt, e) => path match {
        case (i : CIntExpr) :: (j : CIntExpr) :: ps =>
          exp(e, env, CIntExpr((i * n) + j) :: ps, cont)

        case (i : CIntExpr) :: Nil =>
          exp(e, env, CIntExpr(i * n) :: Nil, {
            case ArraySubscript(v, idx) =>
              // emit something like: ((struct float4 *)v)[idx]
              val ptrType = C.AST.PointerType(typ(VectorType(n, dt)))
              cont( C.AST.ArraySubscript(C.AST.Cast(ptrType, v), idx) )
          })
        case _ =>           error(s"Expected path to be not empty")
      }
      case AsScalar(_, m, _, e) => path match {
        case (i: CIntExpr) :: ps =>     exp(e, env, CIntExpr(i / m) :: ps, cont)
        case _ =>           error(s"Expected path to be not empty")
      }
      case _ =>             super.exp(phrase, env, path, cont)
    }
  }

  override def typ(dt: DataType): Type = {
    dt match {
      case v: idealised.DPIA.Types.VectorType =>
        // this sets the representation of vector types in C:
        // struct float4 {
        //    float data[4];
        // };
        C.AST.StructType(v.toString,
          immutable.Seq((C.AST.ArrayType(typ(v.elemType), Some(v.size)), "data")))
      case _ => super.typ(dt)
    }
  }

  protected object OpenMPCodeGen {
    def codeGenParFor(n: Nat,
                      dt: DataType,
                      a: Phrase[AccType],
                      i: Identifier[ExpType],
                      o: Phrase[AccType],
                      p: Phrase[CommandType],
                      env: Environment): Stmt = {
      val cI = C.AST.DeclRef(freshName("i_"))
      val range = RangeAdd(0, n, 1)
      val updatedGen = updatedRanges(cI.name, range)

      applySubstitutions(n, env.identEnv) |> (n => {

      val init = C.AST.VarDecl(cI.name, C.AST.Type.int, init = Some(C.AST.ArithmeticExpr(0)))
      val cond = C.AST.BinaryExpr(cI, C.AST.BinaryOperator.<, C.AST.ArithmeticExpr(n))
      val increment = idealised.C.AST.Assignment(cI, C.AST.ArithmeticExpr(NamedVar(cI.name, range) + 1))

      Phrase.substitute(a `@` i, `for` = o, `in` = p) |> (p =>

      env.updatedIdentEnv(i -> cI) |> (env =>

      range.numVals match {
        // iteration count is 0 => skip body; no code to be emitted
        case Cst(0) => C.AST.Comment("iteration count is 0, no loop emitted")
        // iteration count is 1 => no loop
        case Cst(1) =>
          C.AST.Stmts(C.AST.Stmts(
            C.AST.Comment("iteration count is exactly 1, no loop emitted"),
            C.AST.DeclStmt(C.AST.VarDecl(cI.name, C.AST.Type.int, init = Some(C.AST.ArithmeticExpr(0))))),
            updatedGen.cmd(p, env))
        // default case
        case _ =>C.AST.Stmts(
        C.AST.Code("#pragma omp parallel for"),
        C.AST.ForLoop(C.AST.DeclStmt(init), cond, increment,
          C.AST.Block(immutable.Seq(updatedGen.cmd( p, env)))))
      }))})
    }

    def codeGenParForNat(n: Nat,

                         a: Phrase[AccType],
                         i: NatIdentifier,
                         o: Phrase[AccType],
                         p: Phrase[CommandType],
                         env: Environment): Stmt = {

      val cI = C.AST.DeclRef(freshName("i_"))
      val range = RangeAdd(0, n, 1)
      val updatedGen = updatedRanges(cI.name, range)

      applySubstitutions(n, env.identEnv) |> (n => {

      val init = C.AST.VarDecl(cI.name, C.AST.Type.int, init = Some(C.AST.ArithmeticExpr(0)))
      val cond = C.AST.BinaryExpr(cI, C.AST.BinaryOperator.<, C.AST.ArithmeticExpr(n))
      val increment = idealised.C.AST.Assignment(cI, C.AST.ArithmeticExpr(NamedVar(cI.name, range) + 1))

      //FIRST we must substitute in the indexing of o in the phrase
       Phrase.substitute(a `@d` i, `for` = o, `in` = p) |> (p =>
      //THEN and only THEN we can change the type to use the new index var
       PhraseType.substitute(NamedVar(cI.name, range), `for` = i, in = p) |> (p =>

      env.copy(identEnv = env.identEnv.map {
        case (Identifier(name, AccType(dt)), declRef) =>
          (Identifier(name, AccType(DataType.substitute(NamedVar(cI.name, range), `for` = i, in = dt))), declRef)
        case (Identifier(name, ExpType(dt)), declRef) =>
          (Identifier(name, ExpType(DataType.substitute(NamedVar(cI.name, range), `for` = i, in = dt))), declRef)
        case x => x
      }) |> (env =>

      range.numVals match {
        // iteration count is 0 => skip body; no code to be emitted
        case Cst(0) => C.AST.Comment("iteration count is 0, no loop emitted")
        // iteration count is 1 => no loop
//        case Cst(1) =>
//          C.AST.Stmts(C.AST.Stmts(
//            C.AST.Comment("iteration count is exactly 1, no loop emitted"),
//            C.AST.DeclStmt(C.AST.VarDecl(cI.name, C.AST.Type.int, init = Some(C.AST.ArithmeticExpr(0))))),
//            updatedGen.cmd(p, env))
        // default case
        case _ =>C.AST.Stmts(
        C.AST.Code("#pragma omp parallel for"),
        C.AST.ForLoop(C.AST.DeclStmt(init), cond, increment,
          C.AST.Block(immutable.Seq(updatedGen.cmd(p, env)))))
  })))})
    }

    def codeGenParForVec(n: Nat,
                         dt: DataType,
                         a: Phrase[AccType],
                         i: Identifier[ExpType],
                         o: Phrase[AccType],
                         p: Phrase[CommandType],
                         env: Environment): Stmt = {
      val cI = C.AST.DeclRef(freshName("i_"))
      val range = RangeAdd(0, n, 1)
      val updatedGen = updatedRanges(cI.name, range)

      val init = C.AST.VarDecl(cI.name, C.AST.Type.int, init = Some(C.AST.ArithmeticExpr(0)))
      val cond = C.AST.BinaryExpr(cI, C.AST.BinaryOperator.<, C.AST.ArithmeticExpr(n))
      val increment = idealised.C.AST.Assignment(cI, C.AST.ArithmeticExpr(NamedVar(cI.name, range) + 1))

      Phrase.substitute(a `@v` i, `for` = o, `in` = p) |> (p =>

      env.updatedIdentEnv(i -> cI) |> (env =>

      C.AST.Stmts(
        C.AST.Code("#pragma omp simd"),
        C.AST.ForLoop(C.AST.DeclStmt(init), cond, increment,
          C.AST.Block(immutable.Seq(updatedGen.cmd(p, env)))))))
    }

    def codeGenLiteral(d: OperationalSemantics.Data): Expr = {
      d match {
        case VectorData(vector) => CCodeGen.codeGenLiteral(ArrayData(vector))
        case _ => CCodeGen.codeGenLiteral(d)
      }
    }

    def codeGenForeignFunction(funDecl: ForeignFunction.Declaration,
                               inTs: collection.Seq[DataType],
                               outT: DataType,
                               args: collection.Seq[Phrase[ExpType]],
                               env: Environment,
                               ps: Path,
                               cont: Expr => Stmt): Stmt = {
      (outT, ps) match {
        case (_: ScalarType, Nil) =>
          CCodeGen.codeGenForeignFunction(funDecl, inTs, outT, args, env, ps, cont)

        // This has to be generalised at some point ...
        case (VectorType(_, elemType), i :: Nil) =>
          // this is not really generic, to treat all arguments the same ...
          val inTs_ = inTs.map { case VectorType(_, et) => et }
          addDeclaration(
            C.AST.FunDecl(funDecl.name,
              returnType = typ(elemType),
              params = (funDecl.argNames zip inTs_).map {
                case (name, dt) => C.AST.ParamDecl(name, typ(dt))
              },
              body = C.AST.Code(funDecl.body)
            )
          )

          CCodeGen.codeGenForeignCall(funDecl.name, args, env, i :: Nil, cont)

        case _ =>
          throw new Exception(s"Can not generate fun call to $funDecl with current path $ps")
      }
    }
  }
}
