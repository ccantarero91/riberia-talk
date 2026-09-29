## Ribería: recomendando vinos con RAG en Scala
---
- ¿Quién? Cristian Cantarero
- ¿Qué? Un bot que lee la carta de vinos de una foto y te recomienda según tu gusto
- ¿Cómo? Scala + Postgres/pgvector + Ollama

Note:
- Presentación rápida
- Idea: haces una foto a la carta del restaurante y el bot te dice qué pedir
- Todo el backend en Scala: smithy4s, http4s y un bot de Telegram



### ¿Qué es un RAG?
<!-- gif -->
---
**Retrieval-Augmented Generation**
- El LLM no sabe nada de *tus* datos
- Primero **recuperamos** lo relevante de nuestra base de datos
- Después se lo damos al modelo como contexto para **generar** la respuesta

Note:
- El modelo no conoce nuestro catálogo de vinos ni el perfil del usuario
- En vez de reentrenar, buscamos lo relevante y se lo pasamos en el prompt
---
**Gatos y perros, blancos y negros**

| | Blanco | Negro |
|---|---|---|
| Gato | 🐱⚪ | 🐱⚫ |
| Perro | 🐶⚪ | 🐶⚫ |

- Cada cosa es un punto en un espacio: eje *animal* y eje *color*
- "Gato blanco" está cerca de "gato negro" (mismo animal) y de "perro blanco" (mismo color)
- Lejos de "perro negro"

Note:
- Un embedding es convertir algo en un vector de números
- Cosas parecidas quedan cerca en ese espacio
- Aquí solo hay 2 dimensiones; los modelos reales usan cientos o miles
---
**Llevado a vinos**
- Ejes: tinto ↔ blanco, ligero ↔ con cuerpo, joven ↔ crianza, región...
- "Tinto potente de Ribera" cae cerca de un Ribera crianza
- Y lejos de un albariño joven
- Buscar = encontrar los vinos más cercanos a lo que pide el usuario

Note:
- Mismo concepto: el vino y la consulta se convierten en vectores
- La recomendación es "dame los vecinos más cercanos"



### ¿Cómo implementar un RAG?
<!-- gif -->
---
**Las piezas**
1. Modelo de embeddings → texto a vector
2. Base de datos vectorial → guardar y buscar vectores
3. LLM → redactar la respuesta con el contexto

Note:
- No hace falta una base de datos vectorial nueva si ya tienes Postgres
---
**pgvector**
- Extensión de Postgres: nuevo tipo `vector(n)`
- Operadores de distancia e índices (HNSW / IVFFlat)
- Seguimos con SQL, transacciones y joins de siempre

```sql
CREATE EXTENSION IF NOT EXISTS vector;

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
- 1024 porque es la dimensión de bge-m3
- El índice HNSW hace la búsqueda aproximada rápida
---
**Plugins**
<!-- TODO: concretar qué plugins quieres contar -->
- Extensión `vector` en Postgres
- Ollama para servir embeddings y LLM en local

Note:
- (Revisar este punto)
---
**Embeddings desde Scala**
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
- Llamada HTTP normal a Ollama con http4s + circe
- Lo mismo sirve para indexar vinos y para la consulta del usuario



### ¿Qué modelo usar para embeddings?
<!-- gif -->
---
**Lo que importa**
- Idioma: la carta y los usuarios hablan español
- Dimensión del vector (coste de almacenamiento y búsqueda)
- Que corra en local

Note:
- No todos los modelos de embeddings son iguales fuera del inglés
---
**Mi caso: nomic → bge-m3**
- Empecé con `nomic-embed-text`: bien en inglés, flojo en español
- Cambié a `bge-m3`: multilingüe
- Mejores resultados con consultas en español
- Ojo: cambiar de modelo = recalcular todos los embeddings

Note:
- Los vectores de modelos distintos no son comparables
- Hay que reindexar la tabla entera al migrar



### Búsqueda por embeddings con `<=>`
<!-- gif -->
---
**Operadores de pgvector**
- `<->` distancia euclídea
- `<#>` producto interno (negativo)
- `<=>` distancia coseno ← la que usamos

Note:
- Coseno mide el ángulo entre vectores, no la longitud
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
- `<=>` devuelve distancia: 0 es idéntico
- 1 - distancia = similitud, útil para enseñar un score
---
**Ejemplo**
- Consulta: *"tinto con cuerpo para acompañar carne"*
- Top 3: <!-- TODO: poner resultados reales -->

Note:
- Mejor enseñar una consulta real sacada de la base de datos



### Un modelo como lector OCR
<!-- gif -->
---
**Idea**
- Foto de la carta → modelo de visión (`llama-vision`)
- Nos devuelve los vinos que aparecen
- Luego cada vino se busca en la base de datos por embeddings

Note:
- No usamos un OCR clásico: el modelo entiende la estructura de la carta
---
**Prompt de sistema vs prompt de usuario**
- **Sistema**: el rol y las reglas fijas ("eres un extractor, responde solo JSON, no inventes")
- **Usuario**: la tarea concreta + la imagen

```scala
val messages = List(
  ChatMessage("system", systemPrompt), // rol y reglas fijas
  ChatMessage("user", "Extrae los vinos de esta carta.", List(imageBase64))
)
```

Note:
- El system prompt se mantiene igual en todas las llamadas
- El user prompt cambia con cada petición
---
**Salida en JSON para poder parsear**
```json
{ "wines": [
  { "name": "Pago de Carraovejas", "region": "Ribera del Duero", "price": 45.0 }
] }
```

```scala
final case class MenuWine(name: String, region: Option[String], price: Option[Double])
final case class Menu(wines: List[MenuWine])

for
  reply <- client.expect[ChatReply](req) // req lleva format = "json"
  menu  <- IO.fromEither(parser.decode[Menu](reply.message.content))
yield menu
```

Note:
- Ollama permite forzar formato JSON en la respuesta
- Si no parsea, falla en el IO y lo tratamos como error: nada de regex sobre texto libre



### Recortar las imágenes
<!-- gif -->
---
- Una foto de móvil entera = muchísimos tokens
- Recortamos solo la zona de la carta y reducimos resolución
- Menos tokens → más rápido, más barato
- Y cabe en los límites de un modelo público

Note:
- Los modelos de visión cobran/limitan por tamaño de imagen
- Recortar antes de enviar marca la diferencia
---
```scala
def prepare(bytes: Array[Byte], box: Option[Rect] = None, maxSide: Int = 1024): IO[String] =
  IO.blocking {
    val img     = ImageIO.read(new ByteArrayInputStream(bytes))
    val cropped = box.fold(img)(b => img.getSubimage(b.x, b.y, b.w, b.h))
    val scaled  = shrink(cropped, maxSide) // lado máximo 1024 px
    // ... JPEG + Base64
  }
```

Note:
- Código completo en `modules/code` (`ImagePrep.scala`)



### Demo
<!-- gif -->

Note:
- Foto de la carta → bot de Telegram → recomendación



### ¿Preguntas?
![Alt Text](imgs/questions.webp)
