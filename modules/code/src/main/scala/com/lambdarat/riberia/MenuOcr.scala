package com.lambdarat.riberia

import cats.effect.IO
import io.circe.{Codec, parser}
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.client.Client

final case class ChatMessage(role: String, content: String, images: List[String] = Nil) derives Codec.AsObject
final case class ChatRequest(model: String, messages: List[ChatMessage], format: String, stream: Boolean)
    derives Codec.AsObject
final case class ReplyMessage(content: String) derives Codec.AsObject
final case class ChatReply(message: ReplyMessage) derives Codec.AsObject

final case class MenuWine(name: String, region: Option[String], price: Option[Double]) derives Codec.AsObject
final case class Menu(wines: List[MenuWine]) derives Codec.AsObject

/** Reads a wine list with a vision model and returns typed JSON. */
final class MenuOcr(client: Client[IO], ollama: Uri, model: String = "llama3.2-vision"):

  private val systemPrompt =
    """You are a wine list extractor. Answer ONLY with valid JSON shaped like
      |{"wines":[{"name":string,"region":string|null,"price":number|null}]}.
      |If a field is not present, use null. Do not invent wines.""".stripMargin

  def read(imageBase64: String): IO[Menu] =
    val body = ChatRequest(
      model    = model,
      messages = List(
        ChatMessage("system", systemPrompt),
        ChatMessage("user", "Extract the wines from this wine list.", List(imageBase64))
      ),
      format   = "json",
      stream   = false
    )
    val req = Request[IO](Method.POST, ollama / "api" / "chat").withEntity(body)

    for
      reply <- client.expect[ChatReply](req)
      menu  <- IO.fromEither(parser.decode[Menu](reply.message.content))
    yield menu
