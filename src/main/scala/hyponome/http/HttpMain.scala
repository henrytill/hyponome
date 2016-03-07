package hyponome.http

import scala.io.StdIn

object HttpMain extends App {
  val service: HttpService = HttpService().start()
  val nothing: String      = StdIn.readLine()
  val stopped: HttpService = service.stop(); ()
}
