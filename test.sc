
import java.net.Socket
import java.io._
import java.util._
import java.net._
import java.io.InterruptedIOException

//import ScMaBoSS.{BndMbss, CfgMbss, Hints, MaBoSSClient}
import ScMaBoSS._
//import ScMaBoSS.{BndMbss, CfgMbss, Hints, MaBoSSClient}


val hints : Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
val simulation : CfgMbss = CfgMbss.fromFile("Tests2/ToyModel4Sc.cfg", BndMbss.fromFile("Tests2/ToyModel4Sc.bnd"))

//val mcli : MaBoSSClient = new MaBoSSClient(port=4291)
//println("Start Simulation")
//val result_test : Result = mcli.run(simulation,hints) // could also write val result_test = new Result(mcli,simulation,hints)

//println("Finished simulation")

//val simulation : CfgMbss = CfgMbss.fromFile("Tests/CellFateModel.cfg",
//  BndMbss.fromFile("Tests/CellFateModel.bnd"))

//val upMaBoSSTest2 : UPMaBoSS =
//  new UPMaBoSS("Tests2/ToyModel4Sc.upp",simulation,4291,false,true)

//val upMaBoSSTest3 : UPMaBoSS =
//  new UPMaBoSS("Tests2/ToyModel4Sc.upp",CfgMbss.fromFile("Tests2/ToyModel4Sc.cfg",BndMbss.fromFile("Tests2/ToyModel4Sc.bnd")),4291,false,true)

//val resUP3 : UPMbssOutLight = UPMaBoSSTest2.runLight(14)

//val probStat = ((new NetState(simulation.extNodeList.zip(false :: true :: false :: true :: Nil).toMap,simulation),.5) ::
//  (new NetState(simulation.extNodeList.zip(true :: true :: false :: true :: Nil).toMap,simulation),.2) ::
//  (new NetState(simulation.extNodeList.zip(true :: true :: true :: true :: Nil).toMap,simulation),.3) :: Nil)
