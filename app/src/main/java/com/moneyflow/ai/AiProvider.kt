package com.moneyflow.ai

/** Where AI requests go. Each provider keeps its own API key and model. */
enum class AiProvider(
  val label: String,
  val description: String,
  /** null = Gemini native API; otherwise an OpenAI-compatible base URL ("…/v1"). */
  val defaultBaseUrl: String?,
  val keyUrl: String?,
  val defaultModel: String
) {
  GEMINI(
    "Google Gemini", "구글 AI Studio 키. 무료 사용량이 있어요.",
    null, "https://aistudio.google.com/apikey", "gemini-2.5-flash"
  ),
  OPENROUTER(
    "OpenRouter", "여러 회사의 모델을 한 키로. 이름이 ':free'로 끝나는 무료 모델은 하루 사용 횟수 제한이 있어요.",
    "https://openrouter.ai/api/v1", "https://openrouter.ai/keys", "meta-llama/llama-3.3-70b-instruct:free"
  ),
  GROQ(
    "Groq", "빠른 응답. 무료 사용량이 있어요.",
    "https://api.groq.com/openai/v1", "https://console.groq.com/keys", "llama-3.3-70b-versatile"
  ),
  CUSTOM(
    "직접 입력 (OpenAI 호환)", "OpenAI 호환 주소를 직접 입력해요 (예: 직접 운영하는 OmniRoute 서버). https 주소만 쓸 수 있어요.",
    "", null, ""
  );

  val isOpenAiCompatible: Boolean get() = defaultBaseUrl != null
}
