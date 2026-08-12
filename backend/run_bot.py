import os
import sys
from app.main import full_url_analysis
from app.telegram_bot import create_telegram_bot_app

def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
        
    token = os.getenv("TELEGRAM_BOT_TOKEN")
    
    if not token and len(sys.argv) > 1:
        token = sys.argv[1]
        
    if not token:
        print("\n" + "="*70)
        print("[URL Shield] CONFIGURACAO DO BOT DO TELEGRAM")
        print("="*70)
        print("Para colocar o Bot online no Telegram:")
        print("1. Abra o Telegram e procure por: @BotFather")
        print("2. Envie a mensagem: /newbot")
        print("3. Escolha o nome e usuario do seu bot.")
        print("4. Copie o TOKEN gerado (ex: 123456789:ABCdefGhIJKlmNoPQ...)\n")
        print("Depois, execute este comando no terminal:")
        print("   .\\venv\\Scripts\\python run_bot.py SEU_TOKEN_AQUI")
        print("="*70 + "\n")
        return

    print("Iniciando URL Shield Telegram Bot...")
    bot_app = create_telegram_bot_app(token, full_url_analysis)
    print("Bot online no Telegram! Envie uma mensagem com link para o seu bot para testar.")
    bot_app.run_polling()


if __name__ == "__main__":
    main()
