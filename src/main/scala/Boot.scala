import akka.actor.ActorSystem
import akka.http.scaladsl.Http

import scala.concurrent.ExecutionContext
import scala.io.StdIn

object Boot extends App {
  implicit val system: ActorSystem = ActorSystem("github-contrib-graph")
  implicit val executionContext: ExecutionContext = system.dispatcher

  val token = sys.env("GITHUB_TOKEN")
  if (token.length == 0) {
    throw new RuntimeException("Github token can not be empty! Please create a token with " +
      "user read access and provide it as an environment variable: GITHUB_TOKEN=your_token_here")
  }

  val portEnv = if (sys.env.contains("PORT")) sys.env("PORT") else ""
  val port = if (portEnv.length > 0) portEnv.toInt else 8080

  val service = new GithubService(token)
  val bindingFuture = Http()
    .newServerAt("0.0.0.0", port)
    .bindFlow(service.routes)

  println(s"Server online at http://localhost:${port}/\nPress RETURN to stop...")
  StdIn.readChar()
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
