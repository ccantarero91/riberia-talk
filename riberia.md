## Riberia: wine recommendations with RAG in Scala
---
<!-- .slide: data-background-image="https://opengraph.githubassets.com/1/ccantarero91/riberia-talk" data-background-opacity="0.2" -->
- Who? Cristian Cantarero
- What? A bot that reads a wine list from a photo and recommends what to order based on your taste
- Why? Every time I'm in a restaurant I have no idea which wine to pick. I was also taking a course on this stuff, so I built the recommender
- How? Scala 3 + Postgres + Ollama

Note:
- Quick intro, not too long
- The motivation: standing in front of a wine list with no clue what to order
- The idea: you take a photo of the restaurant's wine list and the bot tells you what to order
- The whole backend is Scala: smithy4s, http4s and a Telegram bot. Postgres is the only database, Ollama runs the models



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
**Cats and dogs on two axes**

<img src="imgs/animals.svg" style="height:360px; margin:0" alt="Cats and dogs placed on two axes, with their coordinates">

- Every item gets two numbers: its **vector**

Note:
- Axis 1 is the animal (cat = -1, dog = +1), axis 2 is the colour (black = -1, white = +1)
- An embedding is exactly this: a list of numbers that says where something sits
- Similar things end up close to each other
---
**Doing maths with meaning**

<img src="imgs/equation.svg" style="width:820px; margin:0" alt="black dog minus white dog plus white cat equals black cat">

- Black dog minus white dog leaves only "make it black"
- Add that to a white cat and we get a black cat

Note:
- [1, -1] - [1, 1] + [-1, 1] = [-1, -1]
- [1, -1] - [1, 1] = [0, -2]: subtracting "white dog" removes the "dog" and leaves only the change in colour (white → black)
- With real embeddings this works approximately, not exactly (the classic example: king - man + woman ≈ queen)
---
**Searching = finding the closest arrow**

<img src="imgs/cosine.svg" style="height:330px; margin:0" alt="Query vector compared by cosine with the four animals">

- Cosine compares the **angle**: 1 = same direction, 0 = unrelated, -1 = opposite
- pgvector's <code>&lt;=&gt;</code> returns 1 − cosine: the smaller, the closer

Note:
- The query "a whitish cat" becomes the vector [-0.8, 0.6] and points almost at "white cat"
- Cosine similarity: white cat 0.99, black cat 0.14, white dog -0.14, black dog -0.99
- As `<=>` distance (1 - cosine): 0.01, 0.86, 1.14, 1.99. Order by it ascending and the first result is the white cat
---
**Now with wines: the same idea**

<img src="imgs/wines.svg" style="height:360px; margin:0" alt="Four wines placed on two axes: body and tannins, with their coordinates">

- Each axis describes something about the wine: here **body** and **tannins**, two of the questions in the user's taste profile
- Every wine gets two numbers: its **vector**

Note:
- In Riberia the taste profile asks: wine type, sweetness, flavours, body, tannins and occasion
- Here I picked two of them as axes so you can see the idea
- Similar wines end up close to each other, and the query ("a full-bodied red") is a point too
---
**With real embeddings we don't pick the axes**
- Each wine has **N** coordinates instead of 2: with `bge-m3`, N = 1024

```text
"Ribera crianza"  →  [0.12, -0.53, 0.08, ...]
```

- The model learns the axes by itself: we can't say what each one means
- Axis 17 may mix "red", "astringent" and "oak" in a way no human would name
- Same rule: similar wines are close

Note:
- This is the honest bit: the drawing is a simplification
- We never know what each of the 1024 dimensions represents, we only know the distances make sense


### How to implement a RAG
<!-- gif -->
---
**The pieces**

<img src="imgs/rag-pieces.svg" style="height:400px; margin:0" alt="The app talks to Postgres with pgvector and to an LLM, which uses an embedding model and an answer model">

Note:
- 1. The vector database: Postgres extended with pgvector
- 2. The LLM side has two models: one turns text into vectors (embeddings), the other writes the answer. In Riberia the answer model is a vision model that reads the photo
- The asterisk: the answer model is explained later, in the image part
- You don't need a new vector database if you already have Postgres
---
**Postgres + pgvector**
- We extend plain Postgres with the `pgvector` extension: that's our vector database
- One line to enable it (with Docker, use the `pgvector/pgvector` image)
- New `vector(n)` type, distance operators and indexes. Still plain SQL

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE wines (
  id        BIGSERIAL PRIMARY KEY,
  name      TEXT NOT NULL,
  embedding vector(1024)
);

CREATE INDEX ON wines USING hnsw (embedding vector_cosine_ops);
```

Note:
- Nothing else to install in the application: the extension adds the `vector` type and the distance operators
- 1024 because that's the dimension of bge-m3
- The HNSW index makes approximate search fast



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
**What we do with the embeddings**
- Wines: the description of every wine is embedded once and stored in `wines.embedding`
- Users: the answers of the taste questionnaire are embedded when they ask for a recommendation
- Both go through the **same** model, so they land in the same space

Note:
- The wine descriptions come from Vivino, in English
- The questionnaire answers are in Spanish
- That is why the model matters
---
**My case: nomic → bge-m3**
- I started with `nomic-embed-text`: English only, so Spanish answers never matched English descriptions
- Switched to `bge-m3`: multilingual, both languages share one vector space
- Careful: changing model = recomputing every embedding

Note:
- Vectors from different models are not comparable
- You have to reindex the whole table when you migrate
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
- This was the first path: from a list of wines we recommend. Next: the same thing starting from a photo



### From a photo: a model as an OCR reader
<!-- gif -->
---
**The idea**
- First pass: photo of the wine list → vision model
- It returns the wines it finds
- Each wine is then looked up in the database by embeddings, exactly as before

Note:
- Not a classic OCR: the model understands the structure of the list
- Locally we ran `qwen2.5vl:7b` in Ollama, but the live version uses a cloud model through Ollama Cloud
- Temperature 0 and a strict prompt: we want literal extraction, no invented wines
- The prompt is always the same, only the image changes
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



### Capping the image size
<!-- gif -->
---
- A full phone photo = a huge number of tokens
- We don't crop anything: we just set a **maximum size** (1024 px on the longest side)
- Fewer tokens → faster, cheaper, and it fits the model's context
- Some models have a resolution cliff where the cost jumps sharply

Note:
- Vision models charge and limit by image size
- In Riberia the cap is configurable (default 1024 px) and the image is re-encoded as JPEG, fixing the EXIF orientation
- We'll see it working in the demo
---
```scala
def prepare(bytes: Array[Byte], maxSide: Int = 1024): IO[String] =
  IO.blocking {
    val img    = ImageIO.read(new ByteArrayInputStream(bytes))
    val scaled = shrink(img, maxSide) // longest side: 1024 px
    // ... JPEG + Base64
  }
```

Note:
- Full code in `modules/code` (`ImagePrep.scala`)
- The real project uses scrimage instead of raw ImageIO because it handles EXIF orientation



### Demo
<!-- gif -->

Note:
- Photo of the wine list → Telegram bot → recommendation



### Taking it to production
<!-- gif -->
---
**Where it runs**

<img src="imgs/prod.svg" style="height:380px; margin:0" alt="GitHub builds the images and deploys over SSH to an Oracle Cloud VM running docker compose with the bot, the API, Postgres with pgvector and a local Ollama for embeddings; vision goes to Ollama Cloud and the bot polls Telegram">

Note:
- Goal: a POC at the lowest possible cost, with production security defaults
- Oracle Cloud Always Free ARM VM (2 OCPU, 12 GB): the only free tier that runs the whole stack
- Everything is one docker compose; the bot needs no inbound connectivity (it polls Telegram)
- The vision model is not on the VM: it goes to Ollama Cloud, so no GPU and no heavy model to pull
- The small local Ollama only serves bge-m3, so the existing embeddings stay valid (no re-embedding)
---
**How a deploy works**
- Every push to `main`: CI builds multi-arch images (ARM for the VM) and pushes them to **GHCR**
- A deploy workflow connects over **SSH**, runs `docker compose pull` + `up -d`, and checks `/health`
- Secrets live in a GitHub Environment and in `.env.secrets` on the VM: never in git
- The API requires an **API key** and only the necessary ports are open

Note:
- Hand-written deploy.yml, separate from the generated CI
- Dedicated deploy key, pinned host key, no third-party SSH actions
- Gotcha: the SQL files in `databases/` only run on an empty volume, so schema changes are applied by hand
---
**What the project depends on**
- **Runtime**: Scala 3, cats-effect, http4s, smithy4s, doobie, pureconfig
- **AI**: langchain4j, Ollama (local embeddings), Ollama Cloud (vision), scrimage for the images
- **Data**: Postgres + pgvector; wines scraped from Vivino with jsoup
- **Channels**: Telegram bot (telegramium)
- **Infra**: Docker, GHCR, GitHub Actions, Oracle Cloud

Note:
- The scraper runs on a schedule on the VM and creates the wines people asked for and we did not have
- Swapping the vision provider is a config change (`CELLAR_LLM_CHAT_*`): Ollama Cloud, Groq, OpenAI...



### Questions?
![Alt Text](imgs/questions.webp)
