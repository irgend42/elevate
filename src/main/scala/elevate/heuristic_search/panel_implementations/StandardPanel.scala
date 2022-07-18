package elevate.heuristic_search.panel_implementations

import elevate.core.strategies.basic
import elevate.core.{Failure, Strategy, Success}
import elevate.heuristic_search.util.{SearchSpaceHelper, Solution, hashProgram}
import elevate.heuristic_search.{HeuristicPanel, Runner}

import scala.collection.parallel.CollectionConverters._


// encapsulates definition of neighbourhood
class StandardPanel[P](
                        val runner: Runner[P],
                        val strategies: Seq[Strategy[P]],
                        val afterRewrite: Option[Strategy[P]] = None, // e.g. rewrite normal form
                        val beforeExecution: Option[Strategy[P]] = None, // e.g. code-gen normal form
                        val rewriter: Option[Solution[P] => Seq[Solution[P]]] = None,
                        val importExport: Option[(String => Solution[P], (Solution[P], String) => Unit)]
                      ) extends HeuristicPanel[P] {

  val solutions = new scala.collection.mutable.HashMap[String, Option[Double]]()
  var call = 0

  def checkRewrite(solution: Solution[P], rewrite: Int): Boolean = {


    // get strategies from strings
    var strategiesMap = Map.empty[String, Strategy[P]]
    strategies.foreach(strat => strategiesMap += (strat.toString() -> strat))

    val strategyString = SearchSpaceHelper.getStrategies(Seq(rewrite)).last

    //    println("check rewrite: " + rewrite)
    //    println("strategyString: " + strategyString)

    val strategy = strategyString match {
      case "id" => basic.id[P]
      case _ => strategies.filter(strat => strat.toString().equals(strategyString)).last
    }

    // apply and check
    try {

      val rewriteResult = strategy.apply(solution.expression)

      val result = rewriteResult match {
        case _: Success[P] => Some(new Solution[P](rewriteResult.get, solution.strategies :+ strategy)).filter(runner.checkSolution)
        case _: Failure[P] => None
      }

      result match {
        case Some(_) => {
          //          println("true")
          true
        }
        case None =>

          //          println("false")
          false
      }

    } catch {
      case e: Throwable =>
        //        println("false")
        false
    }
  }

  def getSolution(initial: Solution[P], numbers: Seq[Int]): Option[Solution[P]] = {

    //    println("getSolution for: " + numbers.mkString("[", ", ", "]"))

    // get strategies as string
    val strategiesString = SearchSpaceHelper.getStrategies(numbers)

    // get strategies from strings
    var strategiesMap = Map.empty[String, Strategy[P]]
    strategies.foreach(strat => strategiesMap += (strat.toString() -> strat))

    // rewrite expression
    var solution = initial
    try {

      strategiesString.foreach(strat => {
        //        println("look for: " + strat)
        val strategy = strat match {
          case "id" => basic.id[P]
          case _ => strategiesMap.apply(strat)
        }
        solution = new Solution[P](strategy.apply(solution.expression).get, solution.strategies :+ strategy)
      })
      Some(solution)
    } catch {
      case e: Throwable => {
        throw new Exception("Could not reproduce rewrites: " + e)
        None
      }
    }

    //    println("solution: " + hashSolution(tmp))
    //    println("\n")
  }

  // parallel without checking
  def N3(solution: Solution[P]): Seq[Solution[P]] = {
    call += 1

    val Ns = strategies.par.map(strategy => {
      try {
        val result = strategy.apply(solution.expression)
        result match {
          case _: Success[P] => Some(new Solution[P](result.get, solution.strategies :+ strategy))
          case _: Failure[P] => None
        }
      } catch {
        case e: Throwable => None
      }
    })

    //    Ns.flatten
    Ns.seq.flatten
  }


  def Np(solution: Solution[P]): Seq[Solution[P]] = {

    call += 1

    //    val NsOptions = strategies.map(strategy => {
    val NsOptions = strategies.par.map(strategy => {
      try {

        //        var result: RewriteResult[P] = null


        // check if no race condition happens here
        //        val result = this.synchronized {
        val result = strategy.apply(solution.expression)
        //        }

        //        this.synchronized {
        result match {
          case _: Success[P] => Some(new Solution[P](result.get, solution.strategies :+ strategy)).filter(runner.checkSolution)
          case _: Failure[P] => {
            //              println("failure: " + result.toString)
            None
          }
          //          }
        }
      } catch {
        case e: Throwable => None
      }
    })
    val Ns = NsOptions.seq.flatten
    //    val Ns = NsOptions.flatten

    // add id to neighbourhood (use real id strategy instead of null)
    //        val identity = basic.id[P]

    //    val Ns2 = Ns ++ Set(new Solution[P](solution.expression, solution.strategies :+ identity))

    //    Ns2

    Ns
  }

  def N(solution: Solution[P]): Seq[Solution[P]] = {
    rewriter match {
      // expand strategy mode
      case Some(rewriteFunction) =>

        val result: Seq[Solution[P]] = afterRewrite match {
          case Some(aftermath) =>
            // todo check if normal form can be applied always
            //            println("rewrite: ")
            val candidates = rewriteFunction.apply(solution).map(elem => Solution(aftermath.apply(elem.expression).get, elem.strategies))
            //            println("candidates: " + candidates.size)
            //            println("check")
            val checked = candidates.filter(runner.checkSolution)
            //            val checked = candidates
            //            println("checked: " + checked.size)
            checked
          //            rewriteFunction.apply(solution).map(elem => Solution(aftermath.apply(elem.expression).get, elem.strategies))
          case None =>
            rewriteFunction.apply(solution)
        }

        result

      // default mode
      case None => N_default(solution)
    }
  }

  def N_default(solution: Solution[P]): Seq[Solution[P]] = {

    call += 1

    val NsOptions = strategies.map(strategy => {
      //      val NsOptions  = strategies.par.map(strategy => {
      try {

        val result = strategy.apply(solution.expression)

        //        this.synchronized {

        result match {
          case _: Success[P] => Some(new Solution[P](result.get, solution.strategies :+ strategy)).filter(runner.checkSolution)
          case _: Failure[P] =>
            //              println("failure: " + result.toString)
            None
        }
        //        }
      } catch {
        case e: Throwable => None
      }
    })
    //    val Ns = NsOptions.seq.flatten
    val Ns = NsOptions.flatten

    Ns
  }

  // warning: check size of hashmap
  def f(solution: Solution[P]): Option[Double] = {
    // buffer performance values in hashmap
    solutions.get(hashProgram(solution.expression)) match {
      case Some(value) => solutions.get(hashProgram(solution.expression)).get
      case _ => {
        val performanceValue = runner.execute(solution).performance
        solutions.+=(hashProgram(solution.expression) -> performanceValue)
        performanceValue
      }
    }
  }

  override def importSolution(filename: String): Solution[P] = {
    importExport match {
      case None => throw new Exception("don't know how to read a solution from disk")
      case Some(function) =>
        function._1.apply(filename)
    }
  }

  override def exportSolution(solution: Solution[P], filename: String): Unit = {
    importExport match {
      case None => throw new Exception("don't know how to write a solution to disk")
      case Some(function) => function._2.apply(solution, filename)
    }
  }
}

