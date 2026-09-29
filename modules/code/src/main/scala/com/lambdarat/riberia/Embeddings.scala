package com.lambdarat.riberia

import cats.effect.IO
import io.circe.Codec
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.client.Client

final case class EmbedRequest(model: String, input: String) derives Codec.AsObject
final case class EmbedResponse(embeddings: List[List[Float]]) derives Codec.AsObject

/** Minimal embeddings client for Ollama (`/api/embed`). */
final class Embeddings(client: Client[IO], ollama: Uri, model: String = "bge-m3"):

  def embed(text: String): IO[Vector[Float]] =
    val req = Request[IO](Method.POST, ollama / "api" / "embed")
      .withEntity(EmbedRequest(model, text))

    client
      .expect[EmbedResponse](req)
      .flatMap(r => IO.fromOption(r.embeddings.headOption)(new NoSuchElementException("Ollama returned 0 embeddings")))
      .map(_.toVector)
