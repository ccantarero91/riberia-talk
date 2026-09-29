package com.lambdarat.riberia

import cats.effect.IO
import org.typelevel.doobie.*
import org.typelevel.doobie.implicits.*

final case class Wine(id: Long, name: String, region: Option[String])

/** Cosine-similarity search with pgvector (`<=>`). */
final class WineSearch(xa: Transactor[IO], embeddings: Embeddings):

  def search(query: String, limit: Int = 5): IO[List[Wine]] =
    embeddings.embed(query).flatMap { v =>
      // pgvector accepts the text form '[0.1,0.2,...]'
      val vec = v.mkString("[", ",", "]")
      sql"""SELECT id, name, region
            FROM wines
            ORDER BY embedding <=> $vec::vector
            LIMIT $limit""".query[Wine].to[List].transact(xa)
    }
