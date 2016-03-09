package hyponome

import scala.sys.ShutdownHookThread

import hyponome.http._

object Main extends App {

  val sht: ShutdownHookThread = sys.addShutdownHook(shutdown)

  val service: HttpService = HttpService().start()

  private def shutdown(): Unit = { val stopped = service.stop(); () }
}
