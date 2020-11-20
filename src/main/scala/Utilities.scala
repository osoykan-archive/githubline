import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.ExceptionHandler

trait Utilities {

  protected def timed[T](name: String)(f: => T): T = {
    val start = System.nanoTime()
    val ret = f
    val end = System.nanoTime()
    println(s"[$name] - Time taken: ${(end - start) / 1000 / 1000} ms")
    ret
  }

  protected def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: IllegalArgumentException => ctx => ctx.complete(StatusCodes.BadRequest, ex.getMessage)
    case ex: Throwable => ctx =>
      ex.printStackTrace()
      ctx.complete(StatusCodes.InternalServerError, "There was a problem while processing the request")
  }
}
