import java.security.KeyStore.TrustedCertificateEntry
import Runtime._
import java.net.Socket
import java.io._
import java.util._
import java.net._
import java.io.InterruptedIOException
import scala.collection.immutable.Map

val hints : Hints = Hints(check = false,hexfloat = false,augment = true,overRide = false,verbose = false)
val simulation : CfgMbss = CfgMbss.fromFile("cellcycle_runcfg.cfg", BndMbss.fromFile("cellcycle.bnd"))

val mcli = new MaBoSSClient(port=4291)
println("Start Simulation")
val result_cellcycle= mcli.run(simulation,hints)
mcli.close()
println("Finished simluation")
