import os
import re
import logging
from telegram import Update
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ContextTypes

# Configuração simples de logging
logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s', level=logging.INFO)

# Expressão regular para extrair URLs de mensagens do Telegram
URL_REGEX = r'(https?://[^\s]+|www\.[^\s]+|[a-zA-Z0-9-]+\.[a-zA-Z]{2,}[^\s]*)'

def create_telegram_bot_app(token: str, analyze_func):
    """
    Cria e configura a aplicação do Bot do Telegram para checagem rápida de URLs encaminhadas.
    """
    app = ApplicationBuilder().token(token).build()

    async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
        welcome_text = (
            "🛡️ *URL Shield Bot - Proteção contra Golpes*\n\n"
            "Olá! Envie ou encaminhe qualquer link para mim e eu direi se ele é *SEGURO*, *SUSPEITO* ou *PERIGOSO*!\n\n"
            "Ideal para proteger você e sua família de golpes do Pix, bancos ou lojas falsas."
        )
        if update.message:
            await update.message.reply_text(welcome_text, parse_mode="Markdown")

    async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
        if not update.message or not update.message.text:
            return
            
        text = update.message.text
        urls = re.findall(URL_REGEX, text)
        
        if not urls:
            await update.message.reply_text("Por favor, me envie uma mensagem que contenha um link de site (ex: www.google.com).")
            return
            
        target_url = urls[0]
        await update.message.reply_text(f"🔍 *Analisando o link*: `{target_url}`\nPor favor, aguarde alguns segundos...", parse_mode="Markdown")
        
        # Executa análise
        report = analyze_func(target_url)
        
        level = report["heuristics"]["risk_level"]
        score = report["heuristics"]["risk_score"]
        summary = report["accessible_explanation"]["summary"]
        recommendation = report["accessible_explanation"]["recommendation"]
        reasons = report["accessible_explanation"]["reasons"]
        
        emoji = "✅" if level == "SEGURO" else ("⚠️" if level == "SUSPEITO" else "🚨")
        
        reply = f"{emoji} *STATUS: {level}* (Risco: {score}%)\n\n"
        reply += f"📝 *Resumo*: {summary}\n\n"
        reply += f"💡 *Recomendação*: {recommendation}\n"
        
        if reasons:
            reply += "\n📌 *Motivos Identificados*:\n"
            for r in reasons[:3]:
                reply += f"• {r}\n"
                
        await update.message.reply_text(reply, parse_mode="Markdown")

    app.add_handler(CommandHandler("start", start))
    app.add_handler(MessageHandler(filters.TEXT & (~filters.COMMAND), handle_message))
    
    return app
