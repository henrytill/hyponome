package hyponome.http

import scala.io.StdIn

object HttpMain extends App {
  val service = HttpService().start()
  val _       = StdIn.readLine()
  val stopped = service.stop(); ()
}
