# Ribería talk

Talk about **Ribería**: wine recommendations with RAG (pgvector + embeddings) and reading wine lists with a vision model, all in Scala.

- `modules/talk`: slides written in markdown, presented with [reveal.js](https://revealjs.com/) and processed with [`mdoc`](https://scalameta.org/mdoc/)
- `modules/code`: the Scala code behind the demo, compiled in CI so the slide snippets don't rot

## Build the slides

1. Install [`sbt`](https://www.scala-sbt.org/) and Java 21
2. Run `sbt mdoc`
3. Serve `modules/talk/target/mdoc` (the `index.html`), e.g. with [`livereload`](https://github.com/lepture/python-livereload)

## Publishing

Every push to `main` compiles everything and publishes to the `gh-pages` branch (workflow `.github/workflows/ci.yml`).
