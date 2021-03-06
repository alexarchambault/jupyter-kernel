package jupyter.kernel.protocol

sealed abstract class ShellReply extends Product with Serializable

object ShellReply {

  sealed abstract class Status extends Product with Serializable

  object Status {
    case object Ok extends Status
    case object Abort extends Status
    case object Error extends Status

    // required for the type class derivation to be fine in 2.10
    type Ok = Ok.type
    type Abort = Abort.type
    type Error = Error.type
  }


  final case class Error(
    ename: String,
    evalue: String,
    traceback: List[String],
    status: Status.Error, // no default value here for the value not to be swallowed by the JSON encoder
    execution_count: Int = -1 // required in some context (e.g. errored execute_reply from jupyter console)
  ) extends ShellReply

  object Error {
    def apply(
      ename: String,
      evalue: String,
      traceback: List[String]
    ): Error =
      Error(
        ename,
        evalue,
        traceback,
        Status.Error
      )

    def apply(
      ename: String,
      evalue: String,
      traceback: List[String],
      execution_count: Int
    ): Error =
      Error(
        ename,
        evalue,
        traceback,
        Status.Error,
        execution_count
      )
  }

  final case class Abort(
    status: Status.Abort // no default value here for the value not to be swallowed by the JSON encoder
  ) extends ShellReply

  object Abort {
    def apply(): Abort =
      Abort(Status.Abort)
  }


  // payloads not supported here
  final case class Execute(
    execution_count: Int,
    user_expressions: Map[String, String],
    status: Status.Ok // no default value here for the value not to be swallowed by the JSON encoder
  ) extends ShellReply

  object Execute {
    def apply(
      execution_count: Int,
      user_expressions: Map[String, String]
    ): Execute =
      Execute(
        execution_count,
        user_expressions,
        Status.Ok
      )
  }

  final case class Inspect(
    found: Boolean,
    data: Map[String, String],
    metadata: Map[String, String],
    status: Status.Ok // no default value here for the value not to be swallowed by the JSON encoder
  ) extends ShellReply

  object Inspect {
    def apply(
      found: Boolean,
      data: Map[String, String],
      metadata: Map[String, String]
    ): Inspect =
      Inspect(
        found,
        data,
        metadata,
        Status.Ok
      )
  }

  final case class Complete(
    matches: List[String],
    cursor_start: Int,
    cursor_end: Int,
    metadata: Map[String, String],
    status: Status.Ok
  ) extends ShellReply

  object Complete {
    def apply(
      matches: List[String],
      cursor_start: Int,
      cursor_end: Int,
      metadata: Map[String, String]
    ): Complete =
      Complete(
        matches,
        cursor_start,
        cursor_end,
        metadata,
        Status.Ok
      )
  }

  sealed abstract class History extends ShellReply

  object History {

    final case class Default(
      history: List[(Int, Int, String)],
      status: Status.Ok
    ) extends History

    object Default {
      def apply(
        history: List[(Int, Int, String)]
      ): Default =
        Default(
          history,
          Status.Ok
        )
    }

    final case class WithOutput(
      history: List[(Int, Int, (String, String))], // FIXME Not sure about the type of ._3._2 of the elements
      status: Status.Ok
    ) extends History

    object WithOutput {
      def apply(
        history: List[(Int, Int, (String, String))]
      ): WithOutput =
        WithOutput(
          history,
          Status.Ok
        )
    }

  }

  sealed abstract class IsComplete extends ShellReply {
    def status: String
  }

  object IsComplete {
    case object Complete extends IsComplete {
      def status = "complete"
    }
    final case class Incomplete(indent: String) extends IsComplete {
      def status = "incomplete"
    }
    case object Invalid extends IsComplete {
      def status = "invalid"
    }
    case object Unknown extends IsComplete {
      def status = "unknown"
    }
  }

  final case class Connect(
    shell_port: Int,
    iopub_port: Int,
    stdin_port: Int,
    hb_port: Int
  ) extends ShellReply

  final case class CommInfo(
    comms: Map[String, CommInfo.Info]
  ) extends ShellReply

  object CommInfo {
    final case class Info(target_name: String)
  }

  final case class KernelInfo(
    protocol_version: String, // X.Y.Z
    implementation: String,
    implementation_version: String, // X.Y.Z
    language_info: KernelInfo.LanguageInfo,
    banner: String,
    help_links: Option[List[KernelInfo.Link]] = None
  ) extends ShellReply

  object KernelInfo {
    final case class LanguageInfo(
      name: String,
      version: String, // X.Y.Z
      mimetype: String,
      file_extension: String, // including the dot
      nbconvert_exporter: String,
      pygments_lexer: Option[String] = None, // only needed if it differs from name
      codemirror_mode: Option[String] = None // only needed if it differs from name - FIXME could be a dict too?
    )

    final case class Link(text: String, url: String)
  }

  final case class Shutdown(
    restart: Boolean
  ) extends ShellReply

}
