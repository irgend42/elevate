package benchmarks.core

import idealised.OpenCL.{Kernel, KernelWithSizes}
import idealised.SurfaceLanguage.Types.{DataType, TypeInference}
import lift.arithmetic.ArithExpr

import scala.util.Random


abstract class RunOpenCLProgram(val verbose:Boolean) {
  import idealised.SurfaceLanguage._
  //The Scala type representing the input data
  type Input
  //The type of the summary structure recording data about the runs
  type Summary

  def dpiaProgram:Expr[DataType -> DataType]

  protected def makeInput(random:Random):Input

  def makeSummary(localSize:Int, globalSize:Int, code:String, runtimeMs:Double, correctness: CorrectnessCheck):Summary

  protected def runScalaProgram(input:Input):Array[Float]

  private def compile(localSize:ArithExpr, globalSize:ArithExpr):KernelWithSizes = {
    val kernel = idealised.OpenCL.KernelGenerator.makeCode(localSize, globalSize)(TypeInference(this.dpiaProgram, Map()).toPhrase)

    if(verbose) {
      println(kernel.code)
    }
    kernel
  }

  final def run(localSize:Int, globalSize:Int):Summary = {
    opencl.executor.Executor.loadAndInit()

    val rand = new Random()
    val input = makeInput(rand)
    val scalaOutput = runScalaProgram(input)

    import idealised.OpenCL.{ScalaFunction, `(`, `)=>`, _}

    val kernel = this.compile(localSize, globalSize)
    val kernelFun = kernel.as[ScalaFunction`(`Input`)=>`Array[Float]]
    val (kernelOutput, time) = kernelFun(input `;`)

    opencl.executor.Executor.shutdown()

    val correct = CorrectnessCheck(kernelOutput, scalaOutput)

    makeSummary(localSize, globalSize, kernel.code, time.value, correct)
  }
}
