package ppl.dsl.opticvx

import scala.virtualization.lms.common.ScalaOpsPkg
import scala.virtualization.lms.common.{NumericOpsExp, OrderingOpsExp, BooleanOpsExp, IfThenElseExp}
import scala.virtualization.lms.common.{EffectExp, BaseExp, Base}
import scala.virtualization.lms.common.ScalaGenBase
import ppl.delite.framework.ops.{DeliteOpsExp}

import java.io.PrintWriter


trait ObjectiveOps extends Base {

  case class MinimizeObjStatement(x: Rep[Expr]) {
    def over(vs: Rep[OptVar]*) = minimize_over(x,vs)
  }
  def minimize(x: Rep[Expr]) = MinimizeObjStatement(x)

  def minimize_over(x: Rep[Expr], vs: Seq[Rep[OptVar]]): Rep[Unit]
}

trait ObjectiveOpsExp extends ObjectiveOps
  with NumericOpsExp with OrderingOpsExp with BooleanOpsExp with EffectExp {
  self: ExprOpsExp with ExprShapeOpsExp with ConstraintOpsExp with MatlabCVXOpsExp
    with OptVarOpsExp with SolverOpsExp with VectorOpsExp with AbstractMatrixOpsExp =>

  def minimize_over(x: Exp[Expr], vs: Seq[Exp[OptVar]]): Exp[Unit] = {
    val cx = canonicalize(x)
    //check that the expression to minimize is scalar and convex
    if(!(cx.vexity() <= Vexity.convex)) {
      throw new Exception("Could not minimize non-convex expression.")
    }
    canonicalize(cx.shape()) match {
      case sh: ExprShapeScalarExp => 
      case _ => throw new Exception("Could not minimize non-scalar expression.")
    }
    //bind all the variables to optimize over
    for(v <- vs) {
      val cv = canonicalize(v)
      if(cv.bound == true) {
        throw new Exception("Variable in optimize-set was already bound to another optimization statement.")
      }
      cv.bound = true
    }
    //accumulate the set of all variables that are connected to this objective by constraints
    var convars: Set[OptVarTr] = cx.vars()
    for(v <- vs) {
      val cv = canonicalize(v)
      convars += cv
      convars ++= cv.vars()
    }
    //iterate, gathering more vars until stable
    {
      var next_convars: Set[OptVarTr] = convars
      do {
        convars = next_convars
        for(vv <- convars) {
          for(constraint <- vv.constraints) {
            next_convars ++= constraint.vars()
          }
        }
      } while(next_convars != convars)
    }
    //verify that all the connected vars are bound
    for(v <- convars) {
      if(v.bound == false) {
        println("Found partial optimization statement over " + vs.length + " variables; not solving yet.")
        return
      }
    }
    println("Encountered optimization statement over " + vs.length + " variables (reduced to " + convars.size + "); proceeding to transform.")
    //collect all the constraints
    var constraints: Set[Constraint] = Set()
    for(v <- convars) {
      constraints ++= v.constraints
    }
    //DEBUG display the variables and constraints
    println("Partially-transformed problem: ")
    println("  variables ->")
    var strout = "    "
    for(v <- convars) {
      strout += v + " " //"(" + v.size + ") "
    }
    println(strout)
    println("  constraints ->")
    for(c <- constraints) {
      println("    " + c)
    }
    //we now assign limits to the variables
    var problem_size: Exp[Int] = Const(0)
    for(v <- convars) {
      if(v.solved == false) {
        v.lookup_offset = problem_size
        problem_size = problem_size + v.size
      }
    }
    //output the problem in matlab
    print(matlab_make_problem(cx,constraints,problem_size))
    //sort the constraints
    val unconstrained_sz = problem_size
    var psimplex_sz: Exp[Int] = Const(0)
    var soc_ns: Seq[Exp[Int]] = Seq()
    var definite_ns: Seq[Exp[Int]] = Seq()
    var zero_exps: Seq[ExprTr] = Seq()
    for(c <- constraints) {
      c match {
        case ConstrainZero(x: Exp[Expr]) =>
          zero_exps :+= canonicalize(x)
        case _ =>
      }
    }
    for(c <- constraints) {
      c match {
        case ConstrainNonnegative(x: Exp[Expr]) =>
          val vcx = new OptVarExp(scalar())
          vcx.lookup_offset = problem_size
          zero_exps :+= canonicalize(x - vcx)
          problem_size = problem_size + Const(1)
          psimplex_sz = psimplex_sz + Const(1)
        case _ =>
      }
    }
    for(c <- constraints) {
      c match {
        case ConstrainSecondOrderCone(x: Exp[Expr], z: Exp[Expr]) =>
          val vcx = new OptVarExp(x.shape())
          vcx.lookup_offset = problem_size
          zero_exps :+= canonicalize(x - vcx)
          problem_size = problem_size + x.size
          val vcz = new OptVarExp(scalar())
          vcz.lookup_offset = problem_size
          zero_exps :+= canonicalize(z - vcz)
          problem_size = problem_size + Const(1)
          canonicalize(x.shape()) match {
            case ExprShapeVectorExp(n) =>
              soc_ns :+= n
            case _ =>
              throw new Exception("Internal Error: Invalid shape on SOC constraint.")
          }
        case _ =>
      }
    }
    for(c <- constraints) {
      c match {
        case ConstrainSemidefinite(x: Exp[Expr]) =>
          val vcx = new OptVarExp(x.shape())
          vcx.lookup_offset = problem_size
          zero_exps :+= canonicalize(x - vcx)
          problem_size = problem_size + x.size
          canonicalize(x.shape()) match {
            case ExprShapeSMatrixExp(n) =>
              definite_ns :+= n
            case _ =>
              throw new Exception("Internal Error: Invalid shape on definiteness constraint.")
          }
        case _ =>
      }
    }
    //convert into standard form
    val stdA = new ExprSeqMatrix(zero_exps, problem_size)
    var stdB = vector_zeros(Const(0))
    for(x <- zero_exps) {
      stdB = vector_cat(stdB, x.get_b())
    }
    val stdC = cx.get_ATy(vector1(Const(1.0)), problem_size)
    val stdK = SymmetricCone(unconstrained_sz, psimplex_sz, soc_ns, definite_ns)
    //invoke the solver
    val solution = solve(stdA, stdB, stdC, stdK)
    //distribute the solution
    for(v <- convars) {
      if(v.solved == false) {
        v.value = vector_sum(v.get_Ax(solution),v.get_b())
        v.lookup_offset = null
        v.solved = true
      }
    }
  }

  class ExprSeqMatrix(es: Seq[ExprTr], sz: Exp[Int]) extends AbstractMatrix {
    def m(): Exp[Int] = {
      var rv: Exp[Int] = Const(0)
      for(e <- es) {
        rv = (rv + e.size)
      }
      rv
    }

    def n(): Exp[Int] = sz

    def get_Ax(x: Exp[CVXVector]): Exp[CVXVector] = {
      var rv: Exp[CVXVector] = vector_zeros(Const(0))
      for(e <- es) {
        rv = vector_cat(rv, e.get_Ax(x))
      }
      rv
    }

    def get_ATy(y: Exp[CVXVector]): Exp[CVXVector] = {
      var rv: Exp[CVXVector] = vector_zeros(sz)
      var ind: Exp[Int] = Const(0)
      for(e <- es) {
        rv = vector_sum(rv, e.get_ATy(vector_select(y, ind, e.size), sz))
        ind = ind + e.size
      }
      rv
    }
  }
}

trait ScalaGenObjectiveOps extends ScalaGenBase {
  val IR: ObjectiveOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = {
    rhs match {
      case _ => 
        super.emitNode(sym, rhs)
    }
  }
}