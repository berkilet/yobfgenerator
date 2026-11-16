package legal.erk.yobf.ai;

/**
 * Bir yapay zekâ sağlayıcısına yapılandırılmış (JSON şemalı) istek gönderen istemci.
 * Uygulamalar: AnthropicIstemcisi (resmi SDK), OpenAiIstemcisi (OpenAI ve uyumlu servisler), GeminiIstemcisi.
 */
public interface ModelIstemcisi extends AutoCloseable {

    /** Yanıtı tur sınıfının JSON şemasına uygun olarak döndürür. */
    <T> T yapilandirilmis(Class<T> tur, String sistem, String kullanici, long maxTokens) throws ModelHatasi;

    /** Durum çubuğunda gösterilecek ad, ör. "OpenAI · gpt-5-mini". */
    String etiket();

    @Override
    default void close() {}
}
