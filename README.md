# Ribería talk

Charla sobre **Ribería**: recomendación de vinos con RAG (pgvector + embeddings) y lectura de cartas con un modelo de visión, todo en Scala.

- `modules/talk`: slides en markdown, presentadas con [reveal.js](https://revealjs.com/) y procesadas con [`mdoc`](https://scalameta.org/mdoc/)
- `modules/code`: código Scala de la demo, compilado en CI para que los snippets de las slides no se pudran

## Compilar las slides

1. Instala [`sbt`](https://www.scala-sbt.org/) y Java 21
2. `sbt mdoc`
3. Sirve `modules/talk/target/mdoc` (el `index.html`), p. ej. con [`livereload`](https://github.com/lepture/python-livereload)

## Publicación

Cada push a `main` compila y publica en la rama `gh-pages` (workflow `.github/workflows/ci.yml`).
Activa Pages en *Settings → Pages → Deploy from branch → `gh-pages` / root* tras el primer despliegue.
