package com.google.ai.edge.litertlm
sealed class Backend { class CPU : Backend(); class GPU : Backend() }
class Contents { companion object { fun of(s: String) = Contents() } ; override fun toString() = "" }
class SamplerConfig(val topK: Int, val topP: Double, val temperature: Double)
class ConversationConfig(val systemInstruction: Contents? = null, val samplerConfig: SamplerConfig? = null)
class EngineConfig(val modelPath: String, val backend: Backend, val maxNumTokens: Int? = null, val cacheDir: String? = null)
class Message { val contents: Contents? = null }
class Conversation : AutoCloseable { fun sendMessage(s: String, m: Map<String, Any> = emptyMap()): Message = Message(); override fun close() {} }
class Engine(c: EngineConfig) : AutoCloseable { fun initialize() {}; fun createConversation(c: ConversationConfig) = Conversation(); override fun close() {} }
