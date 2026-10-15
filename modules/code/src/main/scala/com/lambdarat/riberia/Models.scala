package com.lambdarat.riberia

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.ollama.{OllamaChatModel, OllamaEmbeddingModel}
import dev.langchain4j.model.openai.OpenAiChatModel

/** The two models a RAG needs, built with langchain4j. Swap the provider without touching the rest. */
object Models:

  /** Multilingual embeddings (1024 dimensions), running in a local Ollama. */
  def embeddings(ollamaUrl: String, model: String = "bge-m3"): EmbeddingModel =
    OllamaEmbeddingModel.builder()
      .baseUrl(ollamaUrl)
      .modelName(model)
      .build()

  /** Vision model in a local Ollama, forced to answer with the given JSON schema. */
  def ollamaChat(ollamaUrl: String, model: String, format: ResponseFormat): ChatModel =
    OllamaChatModel.builder()
      .baseUrl(ollamaUrl)
      .modelName(model)
      .temperature(0.0)
      .responseFormat(format)
      .build()

  /** Same, for any provider that speaks the OpenAI API (Ollama Cloud, Groq, OpenAI...). */
  def openAiChat(baseUrl: String, apiKey: String, model: String, format: ResponseFormat): ChatModel =
    OpenAiChatModel.builder()
      .baseUrl(baseUrl)
      .apiKey(apiKey)
      .modelName(model)
      .temperature(0.0)
      .responseFormat(format)
      .build()
