package jupyter.kernel.interpreter

import java.util.concurrent.Executors

import jupyter.kernel.Message
import jupyter.kernel.protocol.{Publish => _, _}
import java.util.UUID

import jupyter.kernel.interpreter.Interpreter._

import scala.util.Random.{nextInt => randomInt}
import argonaut._, Argonaut._
import utest._

import scalaz.concurrent.Strategy

object Helpers {

  implicit val es = Executors.newCachedThreadPool(
    Strategy.DefaultDaemonThreadFactory
  )

  def echoInterpreter(): Interpreter = new Interpreter {
    def interpret(line: String, output: Option[((String) => Unit, (String) => Unit)], storeHistory: Boolean, current: Option[ParsedMessage[_]]) =
      if (line.isEmpty) Error("incomplete")
      else {
        if (storeHistory) executionCount += 1
        if (line startsWith "error:") Error(line stripPrefix "error:") else Value(Seq(DisplayData.text(line)))
      }
    var executionCount = 0
    val languageInfo = ShellReply.KernelInfo.LanguageInfo("echo", "0.1", "text/x-echo", ".echo", "", Some("x-echo"), None)
  }

  def randomConnectReply() = ShellReply.Connect(randomInt(), randomInt(), randomInt(), randomInt())

  def assertCmp[T](resp: Seq[T], exp: Seq[T], pos: Int = 0): Unit = {
    (resp.toList, exp.toList) match {
      case (Nil, Nil) =>
      case (r :: rt, e :: et) =>
        try assert(r == e)
        catch { case ex: utest.AssertionError =>
          println(s"At pos $pos:")
          println(s"Response: $r")
          println(s"Expected: $e")
          throw ex
        }

        assertCmp(rt, et, pos + 1)

      case (Nil, e :: et) =>
        println(s"At pos $pos, missing $e")
        assert(false)

      case (l @ (r :: rt), Nil) =>
        println(s"At pos $pos, extra item(s) $l")
        assert(false)
    }
  }

  final case class Req[T: EncodeJson](msgType: String, t: T) {
    def apply(idents: List[Seq[Byte]], userName: String, sessionId: String, version: Option[String]): (Message, Header) = {
      val msg = ParsedMessage(
        idents, Header(UUID.randomUUID().toString, userName, sessionId, msgType, version), None, Map.empty,
        t
      )

      (msg.toMessage, msg.header)
    }
  }

  sealed abstract class Reply extends Product with Serializable {
    def apply(defaultIdents: List[Seq[Byte]], userName: String, sessionId: String, replyId: String, parHdr: Header, version: Option[String]): (Channel, ParsedMessage[Json])
  }
  final case class ReqReply[T: EncodeJson](msgType: String, t: T) extends Reply {
    def apply(defaultIdents: List[Seq[Byte]], userName: String, sessionId: String, replyId: String, parHdr: Header, version: Option[String]) =
      Channel.Requests -> ParsedMessage(defaultIdents, Header(replyId, userName, sessionId, msgType, version), Some(parHdr), Map.empty, t.asJson)
  }
  final case class PubReply[T: EncodeJson](idents: List[String], msgType: String, t: T) extends Reply {
    def apply(defaultIdents: List[Seq[Byte]], userName: String, sessionId: String, replyId: String, parHdr: Header, version: Option[String]) =
      Channel.Publish -> ParsedMessage(idents.map(_.getBytes("UTF-8").toSeq), Header(replyId, userName, sessionId, msgType, version), Some(parHdr), Map.empty, t.asJson)
  }

  implicit class ParsedMessageOps[T](m: ParsedMessage[T]) {
    def eraseMsgId: ParsedMessage[T] =
      m.copy(
        header = m.header.copy(
          msg_id = ""
        )
      )
  }

  def session(intp: Interpreter, version: Option[String], connectReply: ShellReply.Connect)(msgs: (Req[_], Seq[Reply])*) = {
    val idents = List.empty[Seq[Byte]]
    val userName = "user"
    val sessionId = UUID.randomUUID().toString
    val commonId = UUID.randomUUID().toString
    for (((req, replies), idx) <- msgs.zipWithIndex) {
      val (msg, msgHdr) = req(idents, userName, sessionId, version)
      val expected = replies.map(_(idents, userName, sessionId, commonId, msgHdr, version)).map { case (c, m) => c -> Right(m.eraseMsgId) }
      val response = InterpreterHandler(intp, connectReply, (_, _) => (), msg).right.map(_.runLog.unsafePerformSync.map { case (c, m) => c -> m.decodeAs[Json].right.map(_.eraseMsgId) })
      assert(response.isRight)
      assertCmp(response.right.get, expected)
    }
  }

}
