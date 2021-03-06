package ScMaBoSS

import java.io.FileNotFoundException
import java.nio.file.{Files, Paths}
import scala.util.matching.Regex
import java.io._
import scala.collection.immutable._

import jdk.nashorn.internal.runtime.regexp.RegExp

import scala.io.Source

/** Manage errors when reading file
  *
  */
object ManageInputFile {
  def file_get_content(filename: String): String = {
    if (!Files.exists(Paths.get(filename))) throw new FileNotFoundException(filename + "is not valid")
    val bufferedSource = try {
      Source.fromFile(filename)
    } catch {case _: Throwable => throw new FileNotFoundException(filename + "is not readable")}
    val content = bufferedSource.getLines.mkString("\n")
    bufferedSource.close()
    content
  }
}

/** Method used in different NetState constructors, for handling error
  *
  */
object NetState {
  private def stringToBoolMap(stateString : String,nodeList : List[String]) : Map[String,Boolean] = {
    val activeNodeList = stateString.split(" -- ")
    if (!activeNodeList.toSet.subsetOf(nodeList.toSet))
      println("No node found, take <nil>")
    nodeList.map(node => (node,activeNodeList.contains(node))).toMap
  }
}

/** Network state, with associated node list and error handling
  *
  * @param state
  */
class NetState (val state: Map[String,Boolean]) {
  val nodeSet : Set[String] = state.keySet
  /**
    *
    * @param stateString active nodes separates by " -- "
    * @param nodeList
    * @return
    */
  def this(stateString : String,nodeList : List[String]) =
  this(NetState.stringToBoolMap(stateString,nodeList))

  /**
    *
    * @param stateString active nodes separates by " -- "
    * @param bndMbSS list of nodes from BndMbss
    * @return
    */
  def this(stateString : String,bndMbSS : BndMbss) =
    this(NetState.stringToBoolMap(stateString,bndMbSS.nodeList))


  /** Careful! It uses Cfg external nodes.
    *
    * @param stateString active nodes separates by " -- "
    * @param cfgMbSS list of nodes from external nodes in CfgMbss
    * @return
    */
  def this(stateString : String,cfgMbSS : CfgMbss) =
    this(NetState.stringToBoolMap(stateString, cfgMbSS.extNodeList))

  /**
    *
    * @return active nodes separates by " -- "
    */
  override def toString: String = {
    val activeNodes = state.filter(_._2).keys.toList
    if (activeNodes.isEmpty) "<nil>" else activeNodes.mkString(" -- ")
  }
  private def canEqual(a: Any) = a.isInstanceOf[NetState]
  override def equals(that: Any): Boolean = {
    that match {
      case that:NetState => {
        this.canEqual(that) && (that.state == this.state)
      }
      case _ => false
    }
  }

  override def hashCode(): Int = {
    state.hashCode()
  }
}

/**Companion object, for using input file and generating default configuration
  *
  */
object BndMbss {
  def fromFile(filename : String): BndMbss = {new BndMbss(ManageInputFile.file_get_content(filename))}

  // constants for default cfg
  private val glConfVar : String = "// global configuration variables\ntime_tick = 0.5;" +
    "\nmax_time = 1000;\nsample_count = 10000;\ndiscrete_time = 0;\nuse_physrandgen = 0;" +
    "\nseed_pseudorandom = 0;\ndisplay_traj = 0;\nstatdist_traj_count = 0;\n" +
    "statdist_cluster_threshold = 1;\nthread_count = 1;\nstatdist_similarity_cache_max_size = 20000;\n"
  private val varSet : String = "\n// variables to be set in the configuration file or by using the --config-vars option\n"
  private val setInternal : String = "\n// set is_internal attribute value to 1 if node is an internal node\n"
  private val setRefState : String = "\n// if node is a reference node, set refstate attribute value to 0 or 1 " +
    "according to its reference state\n" +
    "\n// if node is not a reference node, skip its refstate declaration or set value to -1\n"
  private val setIstate : String = "\n// if NODE initial state is: " +
    "\n// - equals to 1: NODE.istate = 1;"+
    "\n// - equals to 0: NODE.istate = 0;"+
    "\n// - random: NODE.istate = -1; OR [NODE].istate = 0.5 [0], 0.5 [1]; OR skip NODE.istate declaration"+
    "\n// - weighted random: [NODE].istate = P0 [0], P1 [1]; where P0 and P1 are arithmetic expressions\n"
}

/**Boolean Network Descriptor for MaBoSS
  *
  * @param bnd
  */
class BndMbss(val bnd : String) {
  private val noCommentBnd = "/\\*[\\s\\S]*\\*/".r.replaceAllIn("//.*".r.replaceAllIn(bnd,""),"")
  private val extVarList : List[String] = "\\$[a-zA-Z_0-9]+".r.findAllIn(noCommentBnd).toList.distinct
  private val nodeFields : List[String] = noCommentBnd.split("[n|N][o|O][d|D][e|E]\\s+").toList.tail.distinct
  val nodeList : List[String] = nodeFields.iterator.map(x => {"[^\\s]+".r.findFirstIn(x) match {
    case Some(node) => node ; case None => null}}).toList

  /**Generate BndMbss with mutations controlled by external variables
    *
    * @param mutNodes
    * @return
    */
  def mutateBnd(mutNodes : List[String]) : BndMbss = {
    val mutNodeFields: String = "node " + nodeFields.map(field => {
      val node = "[^\\s]+".r.findFirstIn(field) match {
        case Some(node) => node;
        case None => null
      }
      if (mutNodes.contains(node)) {
        val rup_field = ("[\\s\\S]*rate_up[\\s\\S]*".r.findFirstIn(field)) match {
          case Some(c) => "rate_up\\s*=([^;]+);".r.
            replaceAllIn(field, "rate_up = ( \\$Low_" + node + " ? 0.0 : ( \\$High_" + node + " ? @max_rate : ($1 ) ) );")
          case None => "\\}".r.replaceAllIn(field, "  rate_up = ( \\$Low_" + node +
            " ? 0.0 : ( \\$High_" + node + " ? @max_rate : (@logic ? 1.0 : 0.0 ) ) );\n}")
        }
        ("[\\s\\S]*rate_down[\\s\\S]*".r.findFirstIn(field)) match {
          case  Some(c) => "rate_down\\s*=([^;]+);".r.
            replaceAllIn(rup_field, "rate_down = ( \\$Low_" + node +
              " ? @max_rate : ( \\$High_" + node + " ? 0.0 : ($1 ) ) );\n" +
              "  max_rate = " + mutNodes.length.toString + ";")
          case None =>
          "\\}".r.replaceAllIn(rup_field, "  rate_down = ( \\$Low_" + node +
            " ? @max_rate : ( \\$High_" + node + " ? 0.0 : (@logic ? 0.0 : 1.0 ) ) );\n" +
            "  max_rate = " + mutNodes.length.toString + ";\n}")
        }
      } else field
    }).mkString("node ")
    new BndMbss(mutNodeFields)
  }

  /**Default configuration
    *
    * @return
    */
  def configTemplate() : String = {
    BndMbss.glConfVar + BndMbss.varSet + extVarList.mkString(" = 0 ; \n") + " = 0 ; \n" +
      BndMbss.setInternal + nodeList.mkString(".is_internal = 0;\n") + ".is_internal = 0;\n" +
      BndMbss.setRefState + nodeList.mkString(".refstate = -1;\n") + ".refstate = -1;\n" +
      BndMbss.setIstate + "[" + nodeList.mkString("].istate = 0.5 [0], 0.5 [1];\n[") + "].istate = 0.5 [0], 0.5 [1];\n"
  }

  def writeToFile(filename : String) : Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write(bnd)
    pw.close()
  }
}

/**Companion object for constructor from file
  *
  */
object CfgMbss {
  def fromFile(filename : String,bndMbss : BndMbss) : CfgMbss = {
    new CfgMbss(bndMbss,ManageInputFile.file_get_content(filename))}

  def fromFiles(filenames : List[String],bndMbss : BndMbss) : CfgMbss = {
    new CfgMbss(bndMbss,filenames.map(x => ManageInputFile.file_get_content(x)).mkString("\n"))}
}

/**Configuration for MaBoSS (including bnd)
  *
  * @param bndMbss
  * @param cfg
  */
class CfgMbss(val bndMbss : BndMbss,val cfg : String) {
  val noCommentCfg = "/\\*[\\s\\S]*\\*/".r.replaceAllIn("//.*".r.replaceAllIn(cfg,""),"")
  /** List of external nodes
    *
    */
  val extNodeList : List[String] = bndMbss.nodeList.
    filter(node => (node+"\\.is_internal\\s*=\\s*TRUE").r.findFirstIn(noCommentCfg).isEmpty).
    filter(node => (node+"\\.is_internal\\s*=\\s*1").r.findFirstIn(noCommentCfg).isEmpty)

  /**Generate CfgMbss with mutations controlled by external variables
    *
    * @param mutNodes
    * @return
    */
  def mutatedCfg(mutNodes: List[String]): CfgMbss = {
    new CfgMbss(bndMbss.mutateBnd(mutNodes),cfg + "\n" + mutNodes.map(node => {
      "$High_" + node + " = 0;\n" + "$Low_" + node + " = 0;"}).mkString("\n"))}

  /**Generate CfgMbss with updated external variables
    *
    * @param newParam
    * @return
    */
  def update(newParam : Map[String,String]) : CfgMbss = {
    def newCfg(cfg:String, listParam : List[(String,String)]) : String = {
      listParam match {
        case Nil => cfg
        case (extV,extVarVal) :: extVVTail => {
          val extV4Regex = if (extV.substring(0, 1) == "$") ("\\" + extV) else extV
          newCfg((extV4Regex + "\\s*=[^;]+;").r.replaceAllIn(cfg, extV4Regex + " = " + extVarVal + ";"), extVVTail)
        }
      }
    }
    new CfgMbss(bndMbss,newCfg(cfg,newParam.toList))
  }

  /** Generate new CfgMbss with initial condition from probability distribution
    * For instance, probTrajLine4Dist result of classes Result and UPMbssOutLight can be used via toList
    *
    * @param probDist
    * @param hex write Double in hexString?
    * @return
    */
  def setInitCond(probDist : List[(NetState,Double)],hex : Boolean = false) : CfgMbss = {
    val firstStateNodes : Set[String] = probDist.head._1.nodeSet
    if (probDist.tail.exists(x=> (x._1.nodeSet != firstStateNodes)))
      throw new IllegalArgumentException("States of probdist are not compatible")
    if (firstStateNodes.union(bndMbss.nodeList.toSet).size > bndMbss.nodeList.length)
      throw new IllegalArgumentException("States of probdist are not compatible with bnd")
    val firstStateNodeList = firstStateNodes.toList
    val newCfg : String = cfg.split("\n").filter(x => "istate".r.findFirstIn(x).isEmpty).mkString("\n") + "\n" +
    "["+firstStateNodeList.mkString(",")+"].istate = "+probDist.map(x=> (if (hex) java.lang.Double.toHexString(x._2) else x._2.toString) +
    " ["+
      firstStateNodeList.map(node => if (x._1.state(node)) 1 else 0).mkString(",")
    + "]").mkString(" , ") +";\n"
    new CfgMbss(bndMbss,newCfg)
  }

  def writeCfgToFile(filename : String) : Unit = {
    val pw = new PrintWriter(new File(filename))
    pw.write(cfg)
    pw.close()
  }
}
