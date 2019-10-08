import java.io.PrintWriter
import java.io._
import org.saddle._
import org.nspl
import org.nspl._
import org.nspl.saddle._
import org.nspl.data._
import org.nspl.awtrenderer._
import org.saddle.io._
import org.saddle.io.CsvImplicits._

case class ClientData(network : String = null, config : String = null, command : String = "Run") {}

case class Hints(check : Boolean = false, hexfloat : Boolean = false, augment : Boolean =  true,
                 overRide : Boolean = false , verbose : Boolean = false) {}

case class ResultData(status : Int = 0, errmsg : String = "" , stat_dist : String = null,
                      prob_traj : String = null, traj : String = null, FP : String = null, runlog : String = null) {}

/** Companion object for updating probtraj line for UPMaBoSS
  *
  */
object Result {
  def updateLine(line : String,divNode: String, deathNode: String): (List[(String, Double)], Double) = { // to be tested
    val nonNormDist = line.split("\t").dropWhile("[0-9].*".r.findFirstIn(_) != None).
      sliding(3, 3).map(x => (x(0), x(1).toDouble)).
      filter(x => !(x._1.split(" -- ").contains(deathNode))).
      map(x => {
        if (x._1.split(" -- ").contains(divNode)) {
          (divNode.r.replaceAllIn((" -- " + divNode).r.replaceAllIn((divNode + " -- ").r.replaceAllIn(x._1, ""), ""), "<nil>"),
            x._2 * 2)
        } else (x._1 , x._2)
      }).toList
    val normFactor = nonNormDist.map(x => x._2).sum
    (nonNormDist.map(x => (x._1, x._2 / normFactor)), normFactor)
  }
}

class Result ( mbcli : MaBoSSClient, simulation : CfgMbss, hints : Hints) {
  /**Generates String or hexString from Double, according to Hints.hexfloat
    *
    * @param double
    * @return
    */
  def doubleToMbssString(double: Double): String = {
    if (hints.hexfloat) java.lang.Double.toHexString(double) else double.toString
  }
  val command: String = if (hints.check) {
    GlCst.CHECK_COMMAND
  } else GlCst.RUN_COMMAND
  val clientData: ClientData = ClientData(simulation.bndMbss.bnd, simulation.cfg, command)
  val data: String = DataStreamer.buildStreamData(clientData, hints)
  val outputData: String = mbcli.send(data)
  val parsedResultData: ResultData = DataStreamer.parseStreamData(outputData, hints)

  /** updates last probability distribution for UPMaBoSS
    *
    * @param divNode   division node
    * @param deathNode death node
    * @return (new_statistical_distribution,normalization_factor)
    */
  def updateLastLine(divNode: String, deathNode: String): (List[(String, Double)], Double) = { // to be tested
    Result.updateLine(parsedResultData.prob_traj.split("\n").toList.last,divNode,deathNode)
  }

  def writeProbTraj2File(filename: String): Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write(parsedResultData.prob_traj)
    pw.close()
  }

  def writeFP2File(filename: String): Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write(parsedResultData.FP)
    pw.close()
  }

  def writeStatDist2File(filename: String): Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write(parsedResultData.stat_dist)
    pw.close()
  }

  def stateTrajectory(netState: NetState): List[(Double, Double)] = { // to be tested, the .isDefined
    parsedResultData.prob_traj.split("\n").toList.tail.map(probTL => {
      val splitProbTL = probTL.split("\t")
      val stateProb: List[(String, Double)] = splitProbTL.dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).
        sliding(3, 3).map(x => (x(0), x(1).toDouble)).toList

      def filterNode(probList: List[(String, Double)], nodeBool: List[(String, Boolean)]): List[(String, Double)] = {
        nodeBool.length match {
          case 0 => probList
          case _ => filterNode(probList.filter(ndProb => !(nodeBool.head._2 ^ ndProb._1.split(" -- ").contains(nodeBool.head._1))), nodeBool.tail)
        }
      }

      (splitProbTL.toList.head.toDouble, filterNode(stateProb, netState.state.toList).map(_._2).sum)
    })
  }

  def nodeTrajectory(node: String): List[(Double, Double)] = // to be tested, the .isDefined
  {
    parsedResultData.prob_traj.split("\n").toList.tail.map(probTL => {
      val splitProbTL = probTL.split("\t")
      (splitProbTL.head.toDouble,
        splitProbTL.dropWhile("^[0-9].*".r.findFirstIn(_).isDefined).
          sliding(3, 3).map(x => (x(0), x(1).toDouble)).filter(_._1.split(" -- ").contains(node)).map(_._2).sum)
    })
  }

  def plotStateTraj(netStates : List[NetState],filename : String) : File = {
     val listTraj=netStates.map(x => stateTrajectory(x))
     val Mat4Plot : Mat[Double]= Mat((Vec(listTraj.head.map(_._1).toArray) ::
       listTraj.map(x => Vec(x.map( y=> y._2).toArray)) :::
       (1 to netStates.length).map(x=>Vec(List.fill(listTraj.head.length)(x.toDouble).toArray)).toList).toArray)
      val builtElement =
        xyplot(Mat4Plot -> (
          (1 to netStates.length).map(x => line(xCol = 0,yCol = x,colorCol = 1+netStates.length+x,
          color = DiscreteColors(netStates.length - 1))).toList :::
          (1 to netStates.length).map(x =>
            point(xCol = 0,yCol = x,colorCol = 1+netStates.length+x,sizeCol=3+2*netStates.length,
              shapeCol=3+2*netStates.length, errorTopCol = 3+2*netStates.length , size = 4d,
              color = DiscreteColors(netStates.length - 1))).toList))(xlab = "Time",ylab="Probability",extraLegend =
        netStates.zipWithIndex.map(x => x._1.toString -> PointLegend(shape = Shape.rectangle(0, 0, 1, 1),
          color = DiscreteColors(netStates.length)(x._2.toDouble) )),ylim = Some(0,1),xWidth = RelFontSize(40d))
    val pdfFile = new File(filename)
  pdfToFile(pdfFile,sequence((builtElement :: Nil),FreeLayout).build)
  }
}
