# URLShield para Android (MVP)

Aplicativo protetor que recebe links antes da navegação, consulta a API do URLShield e apresenta uma decisão acessível antes de abrir o site.

## Fluxo implementado

1. Um familiar ativa o URLShield como aplicativo de navegador padrão.
2. O idoso toca normalmente em um link `http` ou `https`.
3. O Android envia o link ao URLShield.
4. O motor offline (`OfflineAnalyzer`) analisa o link no próprio aparelho: Levenshtein, entropia de Shannon, homógrafos, leetspeak, IP direto e encurtadores.
5. Se o motor offline já indicar perigo, o link é bloqueado na hora. Caso contrário, e se houver servidor configurado, o app chama `POST /api/mobile/analyze` para a análise completa (WHOIS, certificado SSL, DNS).
6. O resultado permite abrir, recomenda cautela ou bloqueia o link.
7. Se o usuário decidir continuar, o site abre no navegador interno protegido.
8. Navegações dentro do mesmo site continuam no navegador protegido; a mudança para outro domínio passa por nova análise.

Se o servidor estiver indisponível (sem internet, API fora do ar ou APK gerado sem servidor), o aplicativo usa o resultado do motor offline e avisa que a consulta completa não foi feita. Os pesos e limiares do motor offline são os mesmos de `backend/app/heuristics.py`.

## Pré-requisitos

- Android Studio compatível com Android Gradle Plugin 9.4
- JDK 17
- Android SDK 36 e Build Tools 36 ou superior
- Backend URLShield em execução

## Gerar o APK

O script `build-apk.ps1` usa o JDK 17, o Android SDK e o Gradle das pastas `work/jdk17`, `work/android-sdk` e `work/gradle-home`:

```powershell
cd android
.\build-apk.ps1                                         # somente offline: funciona sem servidor
.\build-apk.ps1 -ApiUrl https://urlshield-api.onrender.com  # offline + API publicada
.\build-apk.ps1 -ApiUrl http://10.0.2.2:8000                # emulador + API local
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`. Para instalar no celular, copie o arquivo para o aparelho e permita a instalação de fontes desconhecidas.

## Publicar a API (Render)

O arquivo `render.yaml` na raiz do repositório descreve o serviço. No painel do Render, escolha **New > Blueprint**, selecione este repositório e confirme. Depois do deploy, gere o APK com `-ApiUrl` apontando para a URL HTTPS criada. No plano gratuito o servidor hiberna, e a primeira consulta depois de um tempo parado pode passar do tempo limite; nesse caso o app usa o resultado offline.

## Execução local com Android Studio

1. Inicie a API:

   ```powershell
   cd backend
   ..\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```

2. Abra a pasta `android` no Android Studio e adicione `URL_SHIELD_API_URL=http://10.0.2.2:8000` ao `gradle.properties` do usuário (ou rode sem ela para testar só o modo offline).
3. Aguarde a sincronização e execute a variante `debug`.
4. No aplicativo, toque em **Ativar proteção** e confirme o URLShield como navegador padrão.

Para desativar, abra o URLShield, toque em **Desativar proteção** e escolha outro navegador padrão nas configurações do Android.

O build `release` rejeita tráfego HTTP sem criptografia. O HTTP local é permitido somente no manifesto de depuração.

## Limites do MVP

- O protótipo ainda não possui assinatura de produção nem publicação na Play Store.
- A configuração inicial como navegador padrão exige consentimento do usuário ou de um familiar.
- O navegador interno é propositalmente simples e reanalisa navegações principais.
- O motor offline não consulta WHOIS nem valida o certificado SSL; ele só verifica se o link usa `https`.
- Antes de uso real, a API deve estar publicada em HTTPS, autenticada e protegida contra abuso.
