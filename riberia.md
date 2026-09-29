## Ribería: wine recommendations with RAG in Scala
---
- Who? Cristian Cantarero
- What? A bot that reads a wine list from a photo and recommends what to order based on your taste
- How? Scala + Postgres/pgvector + Ollama

Note:
- Quick intro
- The idea: you take a photo of the restaurant's wine list and the bot tells you what to order
- The whole backend is Scala: smithy4s, http4s and a Telegram bot



### What is a RAG?
<!-- gif -->
---
**Retrieval-Augmented Generation**
- The LLM knows nothing about *your* data
- First we **retrieve** what's relevant from our own database
- Then we hand it to the model as context so it can **generate** the answer

Note:
- The model doesn't know our wine catalogue or the user's profile
- Instead of retraining it, we look up what's relevant and put it in the prompt
---
**Black and white cats and dogs**

![Cats and dogs placed on two axes](imgs/animals.svg)

- Every item is a point; its position comes from its features
- Same animal → close on axis 1. Same colour → close on axis 2
- "White cat" is near "black cat" and "white dog", and far from "black dog"

Note:
- An embedding turns something into a vector of numbers, here just 2: (animal, colour)
- Similar things end up close to each other in that space
- "Distance" in this space = how similar two things are
---
**Wine: the same idea, with N dimensions**
- Each wine becomes a point too, but with **N** coordinates instead of 2
- With `bge-m3`, N = 1024

```text
"Ribera del Duero crianza"  →  [ 0.12, -0.53, 0.08, ... ]   // 1024 numbers
```

- We can't name the axes like "animal" or "colour": the model learns them
- Still the same rule: similar wines are close, different wines are far
- The user's query becomes a point in the same space

Note:
- Same concept as the cats and dogs, just with many more axes
- The axes are learned by the model, they are not "red vs white" or "light vs full-bodied" one by one
- Searching = finding the wines closest to the point of the query



### How to implement a RAG
<!-- gif -->
---
**The pieces**
1. Embedding model → text to vector
2. Vector database → store and search vectors
3. LLM → write the answer using that context

Note:
- You don't need a new vector database if you already have Postgres
---
**Enabling pgvector**
- It's a Postgres extension: one line to enable it
- With Docker, just use a Postgres image that already ships it (`pgvector/pgvector`)

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Note:
- Nothing else to install in the application: the extension adds the `vector` type and the distance operators
---
**pgvector**
- Postgres extension: new `vector(n)` type
- Distance operators and indexes (HNSW / IVFFlat)
- Still plain SQL, transactions and joins

```sql
CREATE TABLE wines (
  id        BIGSERIAL PRIMARY KEY,
  name      TEXT NOT NULL,
  region    TEXT,
  notes     TEXT,
  embedding vector(1024)
);

CREATE INDEX ON wines USING hnsw (embedding vector_cosine_ops);
```

Note:
- 1024 because that's the dimension of bge-m3
- The HNSW index makes approximate search fast
---
**Embeddings from Scala**
```scala
final case class EmbedRequest(model: String, input: String) derives Codec.AsObject
final case class EmbedResponse(embeddings: List[List[Float]]) derives Codec.AsObject

def embed(text: String): IO[Vector[Float]] =
  val req = Request[IO](Method.POST, ollama / "api" / "embed")
    .withEntity(EmbedRequest("bge-m3", text))

  client
    .expect[EmbedResponse](req)
    .flatMap(r => IO.fromOption(r.embeddings.headOption)(new NoSuchElementException("0 embeddings")))
    .map(_.toVector)
```

Note:
- A plain HTTP call to Ollama with http4s + circe
- The same function indexes the wines and embeds the user's query



### Which model for embeddings?
<!-- gif -->
---
**What matters**
- Language: not all embedding models are good outside English
- Vector size (storage and search cost)
- Can it run locally?

Note:
- Embedding models are not all equal once you leave English
---
**My case: nomic → bge-m3**
- I started with `nomic-embed-text`: fine in English, weaker in Spanish
- Switched to `bge-m3`: multilingual
- Better results with Spanish queries
- Careful: changing model = recomputing every embedding

Note:
- Vectors from different models are not comparable
- You have to reindex the whole table when you migrate



### Embedding search with `<=>`
<!-- gif -->
---
**pgvector operators**
- `<->` Euclidean distance
- `<#>` (negative) inner product
- `<=>` cosine distance ← the one we use

Note:
- Cosine measures the angle between vectors, not their length
---
```sql
SELECT name, region, 1 - (embedding <=> $1) AS similarity
FROM wines
ORDER BY embedding <=> $1
LIMIT 5;
```

```scala
def search(query: String, limit: Int = 5): IO[List[Wine]] =
  embeddings.embed(query).flatMap { v =>
    val vec = v.mkString("[", ",", "]")
    sql"""SELECT id, name, region
          FROM wines
          ORDER BY embedding <=> $vec::vector
          LIMIT $limit""".query[Wine].to[List].transact(xa)
  }
```

Note:
- `<=>` returns a distance: 0 means identical
- 1 - distance = similarity, handy to show a score
---
**Example**
- Query: *"full-bodied red to go with steak"*
- Top 3: <!-- TODO: put real results here -->

Note:
- Better to show a real query run against the database



### A model as an OCR reader
<!-- gif -->
---
**The idea**
- Photo of the wine list → vision model (`llama-vision`)
- It returns the wines it finds
- Each wine is then looked up in the database by embeddings

Note:
- Not a classic OCR: the model understands the structure of the list
---
**System prompt vs user prompt**
- **System**: the role and fixed rules ("you are an extractor, answer only JSON, don't make things up")
- **User**: the concrete task + the image

```scala
val messages = List(
  ChatMessage("system", systemPrompt), // role and fixed rules
  ChatMessage("user", "Extract the wines from this wine list.", List(imageBase64))
)
```

Note:
- The system prompt is the same in every call
- The user prompt changes with each request
---
**JSON output so we can parse it**
```json
{ "wines": [
  { "name": "Pago de Carraovejas", "region": "Ribera del Duero", "price": 45.0 }
] }
```

```scala
final case class MenuWine(name: String, region: Option[String], price: Option[Double])
final case class Menu(wines: List[MenuWine])

for
  reply <- client.expect[ChatReply](req) // req sets format = "json"
  menu  <- IO.fromEither(parser.decode[Menu](reply.message.content))
yield menu
```

Note:
- Ollama can force the response to be JSON
- If it doesn't parse, the IO fails and we treat it as an error: no regex over free text



### Cropping the images
<!-- gif -->
---
- A full phone photo = a huge number of tokens
- We crop just the wine list area and reduce the resolution
- Fewer tokens → faster and cheaper
- And it fits within the limits of a public model

Note:
- Vision models charge and limit by image size
- Cropping before sending makes a big difference
---
```scala
def prepare(bytes: Array[Byte], box: Option[Rect] = None, maxSide: Int = 1024): IO[String] =
  IO.blocking {
    val img     = ImageIO.read(new ByteArrayInputStream(bytes))
    val cropped = box.fold(img)(b => img.getSubimage(b.x, b.y, b.w, b.h))
    val scaled  = shrink(cropped, maxSide) // longest side: 1024 px
    // ... JPEG + Base64
  }
```

Note:
- Full code in `modules/code` (`ImagePrep.scala`)



### Demo
<!-- gif -->

Note:
- Photo of the wine list → Telegram bot → recommendation



### Questions?
![Alt Text](imgs/questions.webp)
