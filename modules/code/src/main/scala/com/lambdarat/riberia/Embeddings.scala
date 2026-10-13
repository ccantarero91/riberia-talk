package com.lambdarat.riberia

import cats.effect.IO
import dev.langchain4j.model.embedding.EmbeddingModel

/** Turns text into a vector. langchain4j is blocking, hence `IO.blocking`. */
final class Embeddings(model: EmbeddingModel):

  def embed(text: String): IO[Vector[Float]] =
    IO.blocking(model.embed(text).content().vector().toVector)

object Embeddings:

  /** What we embed for a wine: only what describes its taste, no labels like "Wine Color:". */
  def wineText(colour: String, region: String, description: String): String =
    s"Vino $colour. Región: $region. $description"

  /** What we embed for a user: only their answers, never the questions. */
  def tasteText(answers: List[(String, String)]): String =
    answers.map((label, answer) => s"$label: $answer").mkString(". ")
