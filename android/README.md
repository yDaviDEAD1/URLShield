# URLShield para Android (MVP)

Aplicativo protetor que recebe links antes da navegação, consulta a API do URLShield e apresenta uma decisão acessível antes de abrir o site.

## Fluxo implementado

1. Um familiar ativa o URLShield como aplicativo de navegador padrão.
2. O idoso toca normalmente em um link `http` ou `https`.
3. O Android envia o link ao URLShield.
4. O aplicativo chama `POST /api/mobile/analyze` sem abrir o endereço.
5. O resultado permite abrir, recomenda cautela ou bloqueia o link.
6. Se o usuário decidir continuar, o site abre no navegador interno protegido.
7. Navegações dentro do mesmo site continuam no navegador protegido; a mudança para outro domínio passa por nova análise.

Se a API estiver indisponível, o aplicativo falha de forma fechada e não abre o site.

## Pré-requisitos

- Android Studio compatível com Android Gradle Plugin 9.4
- JDK 17
- Android SDK 36 e Build Tools 36 ou superior
- Backend URLShield em execução

## Execução local

No emulador, o build de depuração usa por padrão `http://10.0.2.2:8000`, que aponta para a máquina de desenvolvimento.

1. Inicie a API:

   ```powershell
   cd backend
   ..\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```

2. Abra a pasta `android` no Android Studio.
3. Aguarde a sincronização e execute a variante `debug`.
4. No aplicativo, toque em **Ativar proteção** e confirme o URLShield como navegador padrão.

Para desativar, abra o URLShield, toque em **Desativar proteção** e escolha outro navegador padrão nas configurações do Android.

Para apontar para uma API HTTPS implantada:

```powershell
.\gradlew.bat assembleDebug -PURL_SHIELD_API_URL=https://api.exemplo.com
```

O build `release` rejeita tráfego HTTP sem criptografia. O HTTP local é permitido somente no manifesto de depuração.

## Limites do MVP

- O protótipo ainda não possui assinatura de produção nem publicação na Play Store.
- A configuração inicial como navegador padrão exige consentimento do usuário ou de um familiar.
- O navegador interno é propositalmente simples e reanalisa navegações principais.
- Antes de uso real, a API deve estar publicada em HTTPS, autenticada e protegida contra abuso.
