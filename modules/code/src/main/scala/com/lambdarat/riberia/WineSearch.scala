package com.lambdarat.riberia

import cats.effect.IO
import org.typelevel.doobie.*
import org.typelevel.doobie.implicits.*
import org.typelevel.doobie.postgres.implicits.*

final case class Wine(id: java.util.UUID, name: String, winery: String, distance: Double)

/** Cosine-distance search with pgvector (`<=>`): lower = closer. */
final class WineSearch(xa: Transactor[IO], embeddings: Embeddings):

  def search(tasteText: String, limit: Int = 5): IO[List[Wine]] =
    embeddings.embed(tasteText).flatMap { v =>
      // pgvector accepts the text form '[0.1,0.2,...]'
      val vec = v.mkString("[", ",", "]")
      sql"""SELECT id, name, winery, embedding <=> $vec::vector AS distance
            FROM cellar.wines
            ORDER BY distance
            LIMIT $limit""".query[Wine].to[List].transact(xa)
    }
