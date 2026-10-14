import io
from functools import lru_cache

from gtts import gTTS

MAX_TEXT_LENGTH = 400


@lru_cache(maxsize=256)
def synthesize_speech(text: str) -> bytes:
    """
    Gera o áudio (MP3) da explicação em português do Brasil.
    Usado pelo botão "Ouvir" do site: a voz embutida dos navegadores de celular
    falha em vários aparelhos Android, enquanto um arquivo de áudio toca em todos.
    O cache evita gerar de novo as frases que se repetem (os vereditos são poucos).
    """
    buffer = io.BytesIO()
    gTTS(text=text, lang="pt", tld="com.br", slow=False).write_to_fp(buffer)
    return buffer.getvalue()
