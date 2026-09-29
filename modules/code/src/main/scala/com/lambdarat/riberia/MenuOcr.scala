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

/** Lee una carta de vinos con un modelo de visión y devuelve JSON tipado. */
final class MenuOcr(client: Client[IO], ollama: Uri, model: String = "llama3.2-vision"):

  private val systemPrompt =
    """Eres un extractor de cartas de vinos. Responde SOLO con JSON válido con la forma
      |{"wines":[{"name":string,"region":string|null,"price":number|null}]}.
      |Si un campo no aparece, usa null. No inventes vinos.""".stripMargin

  def read(imageBase64: String): IO[Menu] =
    val body = ChatRequest(
      model    = model,
      messages = List(
        ChatMessage("system", systemPrompt),
        ChatMessage("user", "Extrae los vinos de esta carta.", List(imageBase64))
      ),
      format   = "json",
      stream   = false
    )
    val req = Request[IO](Method.POST, ollama / "api" / "chat").withEntity(body)

    for
      reply <- client.expect[ChatReply](req)
      menu  <- IO.fromEither(parser.decode[Menu](reply.message.content))
    yield menu
