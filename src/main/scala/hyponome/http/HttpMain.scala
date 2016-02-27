package hyponome.http

import scala.io.StdIn

object HttpMain extends App {
  val service = HttpService(HttpService.defaultConfig).start()
  val _       = StdIn.readLine()
  val stopped = service.stop(); ()
}
