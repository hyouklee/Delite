package ppl.delite.runtime.scheduler

import ppl.delite.runtime.Config
import ppl.delite.runtime.graph.DeliteTaskGraph
import ppl.delite.runtime.graph.ops.{OP_Nested, DeliteOP}
import ppl.delite.runtime.graph.targets.Targets
import ppl.delite.runtime.cost._


final class Acc_StaticScheduler extends StaticScheduler with ParallelUtilizationCostModel {

  private val numScala = Config.numThreads
  private val numCPUs = numScala
  private val numCpp = Config.numCpp
  private val gpu = numScala + numCpp
  private val numResources = numScala + numCpp + Config.numCuda + Config.numOpenCL


  def schedule(graph: DeliteTaskGraph) {
    //traverse nesting & schedule sub-graphs, starting with outermost graph
    scheduleFlat(graph)
  }

  protected def scheduleSequential(graph: DeliteTaskGraph) = scheduleFlat(graph, true)

  protected def scheduleFlat(graph: DeliteTaskGraph) = scheduleFlat(graph, false)

  protected def scheduleFlat(graph: DeliteTaskGraph, sequential: Boolean) {
    val opQueue = new OpList
    val schedule = PartialSchedule(numResources)
    enqueueRoots(graph, opQueue)
    while (!opQueue.isEmpty) {
      val op = opQueue.remove
      if (sequential)
        addSequential(op, graph, schedule, gpu) //TODO: sequential should have a resource with it
      else
        scheduleOne(op, graph, schedule)
      enqueueRoots(graph, opQueue)
    }
    ensureScheduled(graph)
    graph.schedule = schedule
  }

  protected def scheduleOne(op: DeliteOP, graph: DeliteTaskGraph, schedule: PartialSchedule) {
    op match {
      case c: OP_Nested => addNested(c, graph, schedule, Range(0, numResources))
      case _ => {
        scheduleOnTarget(op) match {
          case Targets.Scala => scheduleMultiCore(op, graph, schedule, Range(0, numScala))
          case Targets.Cpp => scheduleMultiCore(op, graph, schedule, Range(numScala, numScala+numCpp))
          case Targets.Cuda => scheduleGPU(op, graph, schedule)
          case Targets.OpenCL => scheduleGPU(op, graph, schedule)
        }
      }
    }
  }

  private def scheduleMultiCore(op: DeliteOP, graph: DeliteTaskGraph, schedule: PartialSchedule, resourceList: Seq[Int]) {
    if (op.isDataParallel)
      split(op, graph, schedule, resourceList)
    else
      cluster(op, schedule)
  }

  private def scheduleGPU(op: DeliteOP, graph: DeliteTaskGraph, schedule: PartialSchedule) {
    if (op.isDataParallel)
      split(op, graph, schedule, Seq(gpu))
    else
      scheduleOn(op, schedule, gpu)
  }

  private var nextThread = 0

  private def cluster(op: DeliteOP, schedule: PartialSchedule) {
    //look for best place to put this op (simple nearest-neighbor clustering)
    var i = 0
    var notDone = true
    val deps = op.getDependencies
    while (i < numCPUs && notDone) {
      if (deps.contains(schedule(i).peekLast)) {
        scheduleOn(op, schedule, i)
        notDone = false
        if (nextThread == i) nextThread = (nextThread + 1) % numCPUs
      }
      i += 1
    }
    //else submit op to next thread in the rotation (round-robin)
    if (notDone) {
      scheduleOn(op, schedule, nextThread)
      nextThread = (nextThread + 1) % numCPUs
    }
  }

  //TODO: refactor this
  override protected def split(op: DeliteOP, graph: DeliteTaskGraph, schedule: PartialSchedule, resourceList: Seq[Int]) {
    OpHelper.scheduledTarget(resourceList(0)) match { //TODO: fix - target the same for all resources?
      case Targets.Cuda => splitGPU(op, schedule)
      case Targets.OpenCL => splitGPU(op, schedule)
      case _ => super.split(op, graph, schedule, resourceList)
    }
  }

  private def splitGPU(op: DeliteOP, schedule: PartialSchedule) {
    op.scheduledResource = gpu //TODO: fix this - part of scheduleOn
    val chunk = OpHelper.split(op, 1, "", OpHelper.scheduledTarget(op))(0)
    scheduleOn(chunk, schedule, gpu)
  }

  private def scheduleOnTarget(op: DeliteOP) = {
    if (scheduleOnGPU(op)) {
      if (op.supportsTarget(Targets.Cuda)) Targets.Cuda else Targets.OpenCL
    }
    else if (op.isDataParallel && op.supportsTarget(Targets.Cpp) && numCpp > 0) Targets.Cpp
    else Targets.Scala
  }

}